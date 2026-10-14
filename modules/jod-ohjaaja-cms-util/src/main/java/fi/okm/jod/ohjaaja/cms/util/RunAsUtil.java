/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.util;

import com.liferay.petra.function.UnsafeSupplier;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.security.auth.PrincipalThreadLocal;
import com.liferay.portal.kernel.security.permission.PermissionCheckerFactoryUtil;
import com.liferay.portal.kernel.security.permission.PermissionThreadLocal;
import com.liferay.portal.kernel.service.UserLocalServiceUtil;

/** Runs code with another user's principal and permission checker. */
public final class RunAsUtil {

  private RunAsUtil() {}

  /**
   * Runs the code as the given user. The previous thread-local values are restored afterwards, so
   * the user's rights do not leak to the calling thread.
   */
  public static <T, E extends Throwable> T callAs(User user, UnsafeSupplier<T, E> supplier)
      throws E {
    var originalName = PrincipalThreadLocal.getName();
    var originalPermissionChecker = PermissionThreadLocal.getPermissionChecker();

    try {
      PrincipalThreadLocal.setName(user.getUserId());
      PermissionThreadLocal.setPermissionChecker(PermissionCheckerFactoryUtil.create(user));
      return supplier.get();
    } finally {
      PrincipalThreadLocal.setName(originalName);
      PermissionThreadLocal.setPermissionChecker(originalPermissionChecker);
    }
  }

  /** Runs the code as the company's guest user, i.e. with the rights of an anonymous visitor. */
  public static <T, E extends Throwable> T callAsGuest(
      long companyId, UnsafeSupplier<T, E> supplier) throws E, PortalException {
    return callAs(UserLocalServiceUtil.getGuestUser(companyId), supplier);
  }
}
