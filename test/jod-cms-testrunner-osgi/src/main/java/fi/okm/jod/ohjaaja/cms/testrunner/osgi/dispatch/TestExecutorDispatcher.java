/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture,
 * The Finnish Ministry of Economic Affairs and Employment,
 * The Finnish National Agency of Education (Opetushallitus) and
 * The Finnish Development and Administration centre for ELY Centres
 * and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.testrunner.osgi.dispatch;

import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import org.osgi.framework.Bundle;

/**
 * Invokes the in-container test executor inside the test bundle's classloader via reflection.
 *
 * <p>The test bundle embeds {@code fi.okm.jod.ohjaaja.cms.testrunner.runtime.InContainerTestExecutor}
 * (and its private JUnit/Hamcrest copies). Running the executor inside the test bundle keeps every
 * class load - test class, annotations, JUnit runner - within a single classloader, avoiding the
 * classloader-mismatch issues that arise when JUnit is shared across multiple OSGi bundles.
 */
public final class TestExecutorDispatcher {

  private static final String EXECUTOR_CLASS_NAME =
      "fi.okm.jod.ohjaaja.cms.testrunner.runtime.InContainerTestExecutor";

  private TestExecutorDispatcher() {}

  public static void dispatch(
      Bundle testBundle, String testClassName, List<String> filteredMethods, OutputStream out)
      throws ReflectiveOperationException {

    Class<?> executorClass = testBundle.loadClass(EXECUTOR_CLASS_NAME);
    Method execute =
        executorClass.getMethod("execute", String.class, List.class, OutputStream.class);

    ClassLoader previous = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(executorClass.getClassLoader());
    try {
      execute.invoke(null, testClassName, filteredMethods, out);
    } catch (InvocationTargetException ite) {
      Throwable cause = ite.getTargetException();
      if (cause instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw new IllegalStateException("In-container test executor failed", cause);
    } finally {
      Thread.currentThread().setContextClassLoader(previous);
    }
  }
}
