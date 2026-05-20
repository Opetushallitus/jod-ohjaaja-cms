/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture,
 * The Finnish Ministry of Economic Affairs and Employment,
 * The Finnish National Agency of Education (Opetushallitus) and
 * The Finnish Development and Administration centre for ELY Centres
 * and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.testrunner.osgi.store;

import java.io.ByteArrayInputStream;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;

/** Installs and uninstalls test bundles in the OSGi framework. */
public final class TestBundleStore {

  private static final String LOCATION_PREFIX = "jod-testrunner:";

  private final BundleContext bundleContext;

  public TestBundleStore(BundleContext bundleContext) {
    this.bundleContext = bundleContext;
  }

  /**
   * Installs the given bundle bytes and starts the resulting bundle. If {@link Bundle#start()}
   * fails, the freshly installed bundle is rolled back via {@link Bundle#uninstall()} so a
   * resolution or activation error does not leak a stale bundle into the framework that the
   * client never receives an id for.
   */
  public long install(byte[] bundleBytes) throws BundleException {
    String location = LOCATION_PREFIX + System.nanoTime();
    Bundle bundle = bundleContext.installBundle(location, new ByteArrayInputStream(bundleBytes));
    try {
      bundle.start();
    } catch (BundleException | RuntimeException e) {
      try {
        bundle.uninstall();
      } catch (BundleException uninstallFailure) {
        e.addSuppressed(uninstallFailure);
      }
      throw e;
    }
    return bundle.getBundleId();
  }

  /** Uninstalls the bundle previously installed via {@link #install(byte[])}. */
  public void uninstall(long bundleId) throws BundleException {
    Bundle bundle = bundleContext.getBundle(bundleId);
    if (bundle == null) {
      throw new BundleException("No bundle with id " + bundleId);
    }
    String location = bundle.getLocation();
    if (location == null || !location.startsWith(LOCATION_PREFIX)) {
      throw new BundleException(
          "Refusing to uninstall bundle " + bundleId + " which was not installed by the test runner");
    }
    bundle.uninstall();
  }

  /** Resolves the bundle by id, validating that we installed it. */
  public Bundle get(long bundleId) throws BundleException {
    Bundle bundle = bundleContext.getBundle(bundleId);
    if (bundle == null) {
      throw new BundleException("No bundle with id " + bundleId);
    }
    String location = bundle.getLocation();
    if (location == null || !location.startsWith(LOCATION_PREFIX)) {
      throw new BundleException(
          "Bundle " + bundleId + " was not installed by the test runner");
    }
    return bundle;
  }
}
