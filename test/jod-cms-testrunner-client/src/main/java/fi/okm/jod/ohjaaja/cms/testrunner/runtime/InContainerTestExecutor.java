/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture,
 * The Finnish Ministry of Economic Affairs and Employment,
 * The Finnish National Agency of Education (Opetushallitus) and
 * The Finnish Development and Administration centre for ELY Centres
 * and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.testrunner.runtime;

import java.io.OutputStream;
import java.util.List;
import org.junit.runner.Description;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;

/**
 * Loads the requested test class and runs it with a stock JUnit 4 runner inside the Liferay OSGi
 * container. Streams test events as NDJITEF to the supplied output stream.
 *
 * <p>This class is embedded into every test bundle. Running it from there - rather than from the
 * OSGi runner bundle - means every class referenced during the run (the test class, JUnit, custom
 * test rules) is loaded by the same classloader, which sidesteps the JUnit-annotation classloader
 * mismatch that crops up when JUnit is shared across OSGi bundles.
 *
 * <p>The single entry point {@link #execute(String, List, OutputStream)} is invoked reflectively
 * from {@code fi.okm.jod.ohjaaja.cms.testrunner.osgi.dispatch.TestExecutorDispatcher} - keep the
 * signature stable.
 */
public final class InContainerTestExecutor {

  private InContainerTestExecutor() {}

  /**
   * Entry point called by reflection from the OSGi servlet bundle.
   *
   * @param testClassName fully qualified class name of the test to run
   * @param excludedMethods method names to skip (may be empty/null)
   * @param out output stream to stream NDJITEF events to
   */
  public static void execute(String testClassName, List<String> excludedMethods, OutputStream out)
      throws Exception {

    NdJitefRunListener listener = new NdJitefRunListener(out);

    Class<?> testClass;
    try {
      testClass = Class.forName(testClassName, true, currentClassLoader());
    } catch (Exception | LinkageError t) {
      reportLoadFailure(listener, testClassName, t);
      return;
    }

    BlockJUnit4ClassRunner runner;
    try {
      runner = new BlockJUnit4ClassRunner(testClass);
    } catch (Exception | LinkageError t) {
      reportLoadFailure(listener, testClassName, t);
      return;
    }

    if (excludedMethods != null && !excludedMethods.isEmpty()) {
      try {
        runner.filter(new MethodNameFilter(testClassName, excludedMethods));
      } catch (NoTestsRemainException e) {
        // nothing to run; the host detects this when the stream closes without any test events
        return;
      }
    }

    RunNotifier notifier = new RunNotifier();
    notifier.addListener(listener);
    runner.run(notifier);
  }

  private static void reportLoadFailure(
      NdJitefRunListener listener, String testClassName, Throwable t) throws Exception {
    Description description = Description.createSuiteDescription(testClassName);
    listener.testFailure(new Failure(description, t));
  }

  private static ClassLoader currentClassLoader() {
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    if (cl == null) {
      cl = InContainerTestExecutor.class.getClassLoader();
    }
    return cl;
  }
}
