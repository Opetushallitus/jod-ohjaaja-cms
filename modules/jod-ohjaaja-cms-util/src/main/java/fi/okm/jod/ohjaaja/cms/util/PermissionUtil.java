/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.util;

import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.model.ClassedModel;
import com.liferay.portal.kernel.security.permission.ActionKeys;
import com.liferay.portal.kernel.security.permission.PermissionThreadLocal;
import com.liferay.portal.kernel.security.permission.resource.ModelResourcePermission;

public final class PermissionUtil {

  private PermissionUtil() {}

  /** Whether the current thread's user may view the model; false if there is no model or user. */
  public static <T extends ClassedModel> boolean canView(
      ModelResourcePermission<T> modelResourcePermission, T model) {
    var permissionChecker = PermissionThreadLocal.getPermissionChecker();
    if (model == null || permissionChecker == null) {
      return false;
    }
    try {
      return modelResourcePermission.contains(permissionChecker, model, ActionKeys.VIEW);
    } catch (PortalException e) {
      return false;
    }
  }
}
