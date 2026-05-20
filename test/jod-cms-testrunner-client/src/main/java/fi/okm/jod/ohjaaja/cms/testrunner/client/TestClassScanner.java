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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.HashSet;
import java.util.Set;
import org.junit.Ignore;
import org.junit.runner.RunWith;

/**
 * Walks the compiled test classes directory and returns every class annotated with {@code
 * @RunWith(JodInContainerRunner.class)} (excluding {@code @Ignore}d ones).
 *
 * <p>This list is the working set used by {@link JodInContainerRunner} to decide when the last test
 * has finished and the test bundle can be uninstalled from the Liferay container.
 */
final class TestClassScanner {

  private TestClassScanner() {}

  static Set<Class<?>> findInContainerTestClasses(Class<?> seedClass) {
    ProtectionDomain pd = seedClass.getProtectionDomain();
    CodeSource codeSource = pd.getCodeSource();
    if (codeSource == null) {
      Set<Class<?>> only = new HashSet<>();
      only.add(seedClass);
      return only;
    }

    URL location = codeSource.getLocation();
    Path startPath;
    try {
      startPath = Paths.get(location.toURI());
    } catch (Exception e) {
      Set<Class<?>> only = new HashSet<>();
      only.add(seedClass);
      return only;
    }

    Set<Class<?>> result = new HashSet<>();
    ClassLoader classLoader = seedClass.getClassLoader();

    try {
      Files.walkFileTree(
          startPath,
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
              String rel = startPath.relativize(file).toString();
              if (!rel.endsWith("Test.class")) {
                return FileVisitResult.CONTINUE;
              }
              String name =
                  rel.substring(0, rel.length() - ".class".length())
                      .replace(File.separatorChar, '.');
              try {
                Class<?> candidate = classLoader.loadClass(name);
                RunWith runWith = candidate.getAnnotation(RunWith.class);
                if (runWith == null || runWith.value() != JodInContainerRunner.class) {
                  return FileVisitResult.CONTINUE;
                }
                if (candidate.getAnnotation(Ignore.class) != null) {
                  return FileVisitResult.CONTINUE;
                }
                result.add(candidate);
              } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
                // skip classes we cannot load; the JUnit run will fail on them separately if
                // they were ever supposed to participate.
              }
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException e) {
      throw new IllegalStateException("Failed to scan for in-container test classes", e);
    }

    if (!result.contains(seedClass)) {
      // Fall back to running just the seed if we somehow missed it (e.g. running from a JAR).
      result.clear();
      result.add(seedClass);
    }

    return result;
  }
}
