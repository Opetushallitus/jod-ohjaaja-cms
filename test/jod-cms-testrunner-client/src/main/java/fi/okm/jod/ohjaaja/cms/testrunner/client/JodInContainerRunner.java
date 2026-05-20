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

import fi.okm.jod.ohjaaja.cms.testrunner.client.events.TestEvent;
import fi.okm.jod.ohjaaja.cms.testrunner.client.events.TestEventReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serial;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.Filterable;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;

/**
 * JUnit 4 {@link Runner} that runs the annotated test class inside a Liferay OSGi container.
 *
 * <p>Usage on the test class: {@code @RunWith(JodInContainerRunner.class)}.
 *
 * <p>Lifecycle (shared across every test class in the same Gradle test JVM):
 *
 * <ol>
 *   <li>The first invocation builds one OSGi bundle that embeds every {@code *Test} class in the
 *       current module plus the in-container runtime. JUnit and Hamcrest are deliberately
 *       <em>not</em> embedded - they are imported from the {@code com.liferay.portal.test} bundle
 *       so the same class instances back the test code, the JUnit framework, and Liferay's own
 *       test rules.
 *   <li>That bundle is POSTed to {@code /o/jod-testrunner/bundles} and started inside Liferay.
 *   <li>For each test class, {@code POST /o/jod-testrunner/run} streams the test events back as
 *       NDJITEF. Events are translated into {@link RunNotifier} calls so Gradle picks them up as
 *       normal JUnit results.
 *   <li>After the last test class has run, the bundle is DELETEd.
 * </ol>
 *
 * <p>The base URL of the container is configurable through the {@code jod.testrunner.url} system
 * property. It defaults to {@code http://localhost:8080}.
 */
public class JodInContainerRunner extends Runner implements Filterable {

  private static final Logger log = Logger.getLogger(JodInContainerRunner.class.getName());

  private static final Object stateLock = new Object();
  private static Set<Class<?>> remainingTestClasses;
  private static long bundleId = -1L;
  private static HttpRunnerClient httpClient;
  private static boolean shutdownHookRegistered;

  private final Class<?> testClass;
  private final List<Method> testMethods;
  private final List<String> excludedMethods = new ArrayList<>();

  public JodInContainerRunner(Class<?> testClass) {
    this.testClass = testClass;
    this.testMethods = scanTestMethods(testClass);
  }

  @Override
  public Description getDescription() {
    return Description.createSuiteDescription(testClass);
  }

  @Override
  public void filter(Filter filter) throws NoTestsRemainException {
    Iterator<Method> it = testMethods.iterator();
    while (it.hasNext()) {
      Method method = it.next();
      Description d = Description.createTestDescription(testClass, method.getName());
      if (!filter.shouldRun(d)) {
        excludedMethods.add(method.getName());
        it.remove();
      }
    }
    if (testMethods.isEmpty()) {
      throw new NoTestsRemainException();
    }
  }

  @Override
  public void run(RunNotifier notifier) {
    // Drop @Ignore'd methods up-front, report them as ignored on the host, and add their names
    // to the excluded set so the in-container runner filters them out instead of emitting its own
    // ignored event - otherwise Gradle would receive two ignored notifications per @Ignore method.
    testMethods.removeIf(
        method -> {
          if (method.getAnnotation(Ignore.class) != null) {
            notifier.fireTestIgnored(
                Description.createTestDescription(
                    testClass, method.getName(), method.getAnnotations()));
            excludedMethods.add(method.getName());
            return true;
          }
          return false;
        });

    if (testMethods.isEmpty()) {
      onTestClassDone();
      return;
    }

    try {
      ensureBundleInstalled();
      streamTestRun(notifier);
    } catch (Exception t) {
      notifier.fireTestFailure(new Failure(getDescription(), t));
    } finally {
      onTestClassDone();
    }
  }

  private void streamTestRun(RunNotifier notifier) throws IOException {
    try (InputStream in = httpClient.startRun(bundleId, testClass.getName(), excludedMethods);
        TestEventReader reader = new TestEventReader(in)) {

      TestEvent event;
      while ((event = reader.next()) != null) {
        dispatch(event, notifier);
      }
    }
  }

  private void dispatch(TestEvent event, RunNotifier notifier) {
    TestEvent.Type type = event.type();
    if (type == null) {
      return;
    }
    switch (type) {
      case STARTED:
        notifier.fireTestStarted(
            Description.createTestDescription(event.className(), event.methodName()));
        break;
      case FINISHED:
        notifier.fireTestFinished(
            Description.createTestDescription(event.className(), event.methodName()));
        break;
      case FAILURE:
        notifier.fireTestFailure(
            new Failure(
                Description.createTestDescription(event.className(), event.methodName()),
                toThrowable(event)));
        break;
      case ASSUMPTION_FAILURE:
        notifier.fireTestAssumptionFailed(
            new Failure(
                Description.createTestDescription(event.className(), event.methodName()),
                toThrowable(event)));
        break;
      case IGNORED:
        notifier.fireTestIgnored(
            Description.createTestDescription(event.className(), event.methodName()));
        break;
      case RUN_ERROR:
        notifier.fireTestFailure(
            new Failure(
                getDescription(),
                new RuntimeException(
                    "Container reported run error: " + event.throwableClass() + ": "
                        + event.message())));
        break;
      default:
        // ignore
    }
  }

  private static Throwable toThrowable(TestEvent event) {
    // We deliberately rebuild a plain Throwable here: the original exception type may not be on the
    // host classpath (it could be a Liferay-side class) and we want stack traces to render exactly
    // as they did inside the container.
    String header =
        (event.throwableClass() == null ? "" : event.throwableClass() + ": ")
            + (event.message() == null ? "" : event.message());
    String body = event.stack() == null ? header : event.stack();
    return new ContainerSideFailure(body);
  }

  private void ensureBundleInstalled() throws Exception {
    synchronized (stateLock) {
      if (bundleId != -1L) {
        return;
      }

      if (remainingTestClasses == null) {
        remainingTestClasses = TestClassScanner.findInContainerTestClasses(testClass);
      }

      String baseUrl = System.getProperty("jod.testrunner.url", "http://localhost:8080");
      String contextPath = System.getProperty("jod.testrunner.context", "/o/jod-testrunner");
      httpClient = new HttpRunnerClient(baseUrl + contextPath);

      // The Docker readiness check only waits for the Liferay portal root; the OSGi test runner
      // servlet may still be deploying when this class tries to install its bundle. Ping the
      // servlet first so a transient startup race fails fast with a clear message instead of
      // surfacing as a generic install failure.
      httpClient.waitForReady();

      byte[] bundleBytes = TestBundleBuilder.build(TestBundleBuilder.classesDirOf(testClass));
      log.log(
          Level.INFO,
          () ->
              "Uploading test bundle ("
                  + bundleBytes.length
                  + " bytes) to "
                  + baseUrl
                  + contextPath);
      bundleId = httpClient.installBundle(bundleBytes);
      log.log(Level.INFO, "Bundle installed in container as id {0}", bundleId);
      registerShutdownHook();
    }
  }

  private void onTestClassDone() {
    synchronized (stateLock) {
      if (remainingTestClasses != null) {
        remainingTestClasses.remove(testClass);
        if (remainingTestClasses.isEmpty() && bundleId != -1L) {
          uninstallBundleQuietly();
        }
      }
    }
  }

  private static void uninstallBundleQuietly() {
    try {
      if (httpClient != null && bundleId != -1L) {
        log.log(Level.INFO, "Uninstalling bundle {0}", bundleId);
        httpClient.uninstallBundle(bundleId);
      }
    } catch (Exception e) {
      log.log(Level.WARNING, e, () -> "Failed to uninstall bundle " + bundleId);
    } finally {
      bundleId = -1L;
      remainingTestClasses = null;
    }
  }

  private static void registerShutdownHook() {
    if (shutdownHookRegistered) {
      return;
    }
    shutdownHookRegistered = true;
    Runtime.getRuntime()
        .addShutdownHook(new Thread(() -> {
          synchronized (stateLock) {
            if (bundleId != -1L) {
              uninstallBundleQuietly();
            }
          }
        }, "jod-testrunner-shutdown"));
  }

  private static List<Method> scanTestMethods(Class<?> clazz) {
    List<Method> methods = new ArrayList<>();
    Class<?> current = clazz;
    while (current != null && current != Object.class) {
      for (Method method : current.getDeclaredMethods()) {
        if (method.getAnnotation(Test.class) != null) {
          methods.add(method);
        }
      }
      current = current.getSuperclass();
    }
    methods.sort(Comparator.comparing(Method::getName));
    return methods;
  }

  /** Lightweight Throwable that renders the container-side stack trace verbatim. */
  private static final class ContainerSideFailure extends Throwable {
    @Serial
    private static final long serialVersionUID = 1L;

    ContainerSideFailure(String message) {
      super(message);
      setStackTrace(new StackTraceElement[0]);
    }

    @Override
    public String toString() {
      return getMessage();
    }
  }
}
