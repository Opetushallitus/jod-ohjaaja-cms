/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture,
 * The Finnish Ministry of Economic Affairs and Employment,
 * The Finnish National Agency of Education (Opetushallitus) and
 * The Finnish Development and Administration centre for ELY Centres
 * and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.testrunner.client;

import aQute.bnd.osgi.Builder;
import aQute.bnd.osgi.Jar;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * Builds an OSGi bundle that contains the user's compiled test classes plus the in-container
 * runtime (JUnit, Hamcrest, {@code fi.okm.jod.ohjaaja.cms.testrunner.runtime.*}). The resulting bundle
 * is uploaded to the Liferay container via {@link HttpRunnerClient#installBundle(byte[])}.
 */
public final class TestBundleBuilder {

  private static final String IMPORT_PACKAGE = "Import-Package";

  private TestBundleBuilder() {}

  /**
   * Builds a bundle that exposes every compiled class under {@code testClassesDir}. The bundle has
   * no exports and no Bundle-Activator; it is just a code library the OSGi runner loads classes
   * from.
   */
  public static byte[] build(File testClassesDir) throws Exception {
    if (testClassesDir == null || !testClassesDir.isDirectory()) {
      throw new IllegalArgumentException(
          "testClassesDir does not exist or is not a directory: " + testClassesDir);
    }

    try (Builder builder = new Builder();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {

      builder.setBase(testClassesDir);
      for (File classpathEntry : classpathEntries()) {
        builder.addClasspath(classpathEntry);
      }

      String symbolicName = "jod-testrunner-" + Long.toHexString(System.nanoTime());
      builder.set("Bundle-SymbolicName", symbolicName);
      builder.set("Bundle-Version", "1.0.0");
      builder.set("Bundle-ManifestVersion", "2");

      // Embed only the bits that Liferay does not provide: Awaitility (plus its private
      // dependencies that we never use) and the testrunner support classes referenced by the
      // test bundle's manifest. JUnit and Hamcrest are intentionally NOT embedded - Liferay's
      // portal.test bundle exports them, so a single wired copy is shared between the test
      // code, the JUnit framework that runs the test, and Liferay's own test rules (such as
      // LiferayIntegrationTestRule). This avoids the LinkageError that arose with the prior
      // embed-and-optionally-import approach.
      //
      // The client package is embedded so the test class's @RunWith(JodInContainerRunner.class)
      // annotation can be materialised inside the container without an unresolvable Import-Package
      // - none of those classes are actually invoked in-container.
      builder.set(
          "Private-Package",
          "org.awaitility.*,"
              + "fi.okm.jod.ohjaaja.cms.testrunner.jitef,"
              + "fi.okm.jod.ohjaaja.cms.testrunner.runtime,"
              + "fi.okm.jod.ohjaaja.cms.testrunner.client,"
              + "fi.okm.jod.ohjaaja.cms.testrunner.client.events");

      // Pick up the compiled test classes from the module's build/classes/java/test directory.
      builder.set("-includeresource", testClassesDir.getAbsolutePath());

      // Imports: let bnd's wildcard discover everything actually referenced - JUnit, Hamcrest
      // and Liferay packages all come from the host. The only excludes are packages that
      // either ship inside the bundle (awaitility, our own testrunner packages) or are
      // referenced only by host-side classes that never run inside the container
      // (aQute.bnd, awaitility's optional cglib/objenesis proxy dependencies).
      builder.set(
          IMPORT_PACKAGE,
          "!aQute.bnd.*,"
              + "!aQute.lib.*,"
              + "!aQute.libg.*,"
              + "!aQute.service.*,"
              + "!org.awaitility.*,"
              + "!net.sf.cglib.*,"
              + "!org.objenesis.*,"
              + "!fi.okm.jod.ohjaaja.cms.testrunner.*,"
              + "*");

      // bnd's default would otherwise pull in JavaPortlet/JavaServlet contracts which are not
      // available.
      builder.set("-contract", "!JavaPortlet,!JavaServlet");

      Jar jar = builder.build();

      List<String> errors = builder.getErrors();
      if (!errors.isEmpty()) {
        throw new IllegalStateException(
            "bnd failed to build test bundle " + symbolicName + ": " + errors);
      }

      stripImportVersions(jar);

      jar.write(out);

      if (Boolean.getBoolean("jod.testrunner.copy.jar")) {
        Path debugCopy = Paths.get(testClassesDir.getAbsolutePath(), symbolicName + ".jar");
        Files.deleteIfExists(debugCopy);
        Files.write(debugCopy, out.toByteArray());
      }

      return out.toByteArray();
    }
  }

  /** Returns {@code build/classes/java/test} relative to the location of the given test class. */
  public static File classesDirOf(Class<?> testClass) {
    ProtectionDomain pd = testClass.getProtectionDomain();
    CodeSource codeSource = pd.getCodeSource();
    if (codeSource == null) {
      throw new IllegalStateException(
          "Cannot determine class location for " + testClass.getName());
    }
    URL location = codeSource.getLocation();
    try {
      File f = new File(location.toURI());
      if (!f.isDirectory()) {
        throw new IllegalStateException(
            "Expected test classes directory but found " + f + " for " + testClass.getName());
      }
      return f;
    } catch (Exception e) {
      throw new IllegalStateException(
          "Cannot resolve class location for " + testClass.getName(), e);
    }
  }

  /**
   * Removes {@code version="..."} version-range constraints from the generated {@code
   * Import-Package} header.
   *
   * <p>bnd computes a version range for every imported package based on the version it sees on the
   * build classpath. When the runtime Liferay container ships a different (typically newer)
   * version of a package, the bundle fails to resolve with errors like {@code Unresolved
   * requirement: Import-Package: com.liferay.foo.bar; version="[6.2.0,7.0.0)"}. We do not control
   * what package versions the container ships, so the safest workaround is to drop the version
   * ranges entirely and let OSGi resolve against whatever is exported at runtime.
   */
  private static void stripImportVersions(Jar jar) throws Exception {
    Manifest manifest = jar.getManifest();
    if (manifest == null) {
      return;
    }
    Attributes attributes = manifest.getMainAttributes();
    String importPackage = attributes.getValue(IMPORT_PACKAGE);
    if (importPackage == null || importPackage.isEmpty()) {
      return;
    }
    attributes.putValue(IMPORT_PACKAGE, removeVersionAttributes(importPackage));
  }

  /**
   * Removes {@code version="..."}, {@code bundle-version="..."} and {@code bundle-symbolic-name=}
   * attributes from a comma-separated Import-Package clause list. Other directives (such as
   * {@code resolution:=optional}) are preserved.
   */
  static String removeVersionAttributes(String header) {
    StringBuilder result = new StringBuilder(header.length());
    int i = 0;
    int len = header.length();
    boolean inQuotes = false;
    int clauseStart = 0;

    while (i <= len) {
      char c = i < len ? header.charAt(i) : '\0';
      if (i < len && c == '"') {
        inQuotes = !inQuotes;
        i++;
        continue;
      }
      if (!inQuotes && (c == ',' || i == len)) {
        String clause = header.substring(clauseStart, i);
        result.append(scrubClause(clause));
        if (c == ',') {
          result.append(',');
        }
        clauseStart = i + 1;
      }
      i++;
    }
    return result.toString();
  }

  private static String scrubClause(String clause) {
    StringBuilder sb = new StringBuilder(clause.length());
    int len = clause.length();
    int segStart = 0;
    int i = 0;
    while (i < len) {
      int next = nextSegmentBoundary(clause, i);
      appendSegmentIfKept(sb, clause, segStart, next);
      segStart = next + 1;
      i = next + 1;
    }
    if (segStart <= len) {
      appendSegmentIfKept(sb, clause, segStart, len);
    }
    return sb.toString();
  }

  /**
   * Returns the index of the next semicolon outside double quotes, starting at {@code from}, or
   * {@code clause.length()} if no further semicolon exists.
   */
  private static int nextSegmentBoundary(String clause, int from) {
    boolean inQuotes = false;
    int len = clause.length();
    for (int i = from; i < len; i++) {
      char c = clause.charAt(i);
      if (c == '"') {
        inQuotes = !inQuotes;
      } else if (c == ';' && !inQuotes) {
        return i;
      }
    }
    return len;
  }

  private static void appendSegmentIfKept(StringBuilder sb, String clause, int start, int end) {
    String segment = clause.substring(start, end).trim();
    if (segment.isEmpty() || isStripped(segment)) {
      return;
    }
    if (!sb.isEmpty()) {
      sb.append(';');
    }
    sb.append(segment);
  }

  private static boolean isStripped(String segment) {
    String lower = segment.toLowerCase(java.util.Locale.ROOT);
    return lower.startsWith("version=")
        || lower.startsWith("bundle-version=")
        || lower.startsWith("bundle-symbolic-name=");
  }

  private static List<File> classpathEntries() {
    String cp = System.getProperty("java.class.path", "");
    List<File> out = new ArrayList<>();
    for (String entry : cp.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
      if (entry.isEmpty()) {
        continue;
      }
      File f = new File(entry);
      if (f.isDirectory() || entry.endsWith(".jar")) {
        out.add(f);
      }
    }
    return out;
  }
}
