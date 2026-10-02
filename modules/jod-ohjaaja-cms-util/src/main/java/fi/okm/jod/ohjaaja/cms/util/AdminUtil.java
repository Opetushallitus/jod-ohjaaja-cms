/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.util;

import com.liferay.petra.function.UnsafeRunnable;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.model.role.RoleConstants;
import com.liferay.portal.kernel.security.auth.PrincipalThreadLocal;
import com.liferay.portal.kernel.security.permission.PermissionCheckerFactoryUtil;
import com.liferay.portal.kernel.security.permission.PermissionThreadLocal;
import com.liferay.portal.kernel.service.RoleLocalServiceUtil;
import com.liferay.portal.kernel.service.UserLocalServiceUtil;
import java.util.List;

public final class AdminUtil {

  private static final Log log = LogFactoryUtil.getLog(AdminUtil.class);

  private AdminUtil() {}

  /** Returns the first Administrator of the company, or the guest user if there is none. */
  public static User getAdminUser(long companyId) throws PortalException {
    var role = RoleLocalServiceUtil.fetchRole(companyId, RoleConstants.ADMINISTRATOR);
    var adminUsers =
        role == null ? List.<User>of() : UserLocalServiceUtil.getRoleUsers(role.getRoleId(), 0, 1);

    if (adminUsers.isEmpty()) {
      log.warn("No admin users found in company " + companyId + ", using guest user");
      return UserLocalServiceUtil.getGuestUser(companyId);
    }

    return adminUsers.getFirst();
  }

  /**
   * Runs the given code with the admin user's principal and permission checker. The previous
   * thread-local values are restored afterwards, so admin rights do not leak to the calling thread.
   */
  public static <E extends Throwable> void runAsAdmin(long companyId, UnsafeRunnable<E> runnable)
      throws E, PortalException {
    var user = getAdminUser(companyId);
    var originalName = PrincipalThreadLocal.getName();
    var originalPermissionChecker = PermissionThreadLocal.getPermissionChecker();

    try {
      PrincipalThreadLocal.setName(user.getUserId());
      PermissionThreadLocal.setPermissionChecker(PermissionCheckerFactoryUtil.create(user));
      runnable.run();
    } finally {
      PrincipalThreadLocal.setName(originalName);
      PermissionThreadLocal.setPermissionChecker(originalPermissionChecker);
    }
  }
}
