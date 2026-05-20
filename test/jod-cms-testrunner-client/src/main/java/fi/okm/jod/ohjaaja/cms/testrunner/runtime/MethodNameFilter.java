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

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.junit.runner.Description;
import org.junit.runner.manipulation.Filter;

/**
 * Filter that removes every test method whose name appears in the given set.
 *
 * <p>The host-side runner uses JUnit's own {@link org.junit.runner.manipulation.Filterable} to
 * narrow a test run when only a subset of methods should execute (for example when Gradle requests
 * a single test via {@code --tests}). The set of excluded names is sent to the container as part of
 * the run request.
 */
public final class MethodNameFilter extends Filter {

  private final String className;
  private final Set<String> excludedMethods;

  public MethodNameFilter(String className, Collection<String> excludedMethods) {
    this.className = className;
    this.excludedMethods = new HashSet<>(excludedMethods);
  }

  @Override
  public boolean shouldRun(Description description) {
    if (description.isSuite()) {
      // keep suites; their child descriptions get filtered individually
      return true;
    }
    if (!className.equals(description.getClassName())) {
      return true;
    }
    return !excludedMethods.contains(description.getMethodName());
  }

  @Override
  public String describe() {
    return "exclude methods " + excludedMethods + " on " + className;
  }
}
