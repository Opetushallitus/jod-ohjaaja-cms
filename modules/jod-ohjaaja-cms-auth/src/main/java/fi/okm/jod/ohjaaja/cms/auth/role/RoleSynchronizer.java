/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.auth.role;

import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.Role;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.model.role.RoleConstants;
import com.liferay.portal.kernel.service.RoleLocalService;
import com.liferay.portal.kernel.service.UserGroupRoleLocalService;
import com.liferay.portal.kernel.service.UserLocalService;
import fi.okm.jod.ohjaaja.cms.util.JodOhjaajaCmsUtil;
import java.util.Collection;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Synchronizes the roles in {@link RoleMapping} from Cognito groups. Roles that are not part of the
 * mapping are left untouched. Site roles are granted on the JOD Ohjaaja CMS site.
 */
@Component(service = RoleSynchronizer.class)
public class RoleSynchronizer {

  private static final Log log = LogFactoryUtil.getLog(RoleSynchronizer.class);

  @Reference private RoleLocalService roleLocalService;
  @Reference private UserLocalService userLocalService;
  @Reference private UserGroupRoleLocalService userGroupRoleLocalService;
  @Reference private JodOhjaajaCmsUtil jodOhjaajaCmsUtil;

  public void synchronize(User user, Collection<String> cognitoGroups) throws PortalException {
    var granted = RoleMapping.resolve(cognitoGroups);
    for (var mappedRole : RoleMapping.MAPPED_ROLES) {
      var role = roleLocalService.fetchRole(user.getCompanyId(), mappedRole.roleName());
      if (role == null) {
        log.warn("Mapped role " + mappedRole.roleName() + " does not exist");
        continue;
      }
      var shouldHave = granted.contains(mappedRole);
      switch (role.getType()) {
        case RoleConstants.TYPE_REGULAR -> synchronizeRegularRole(user, role, shouldHave);
        case RoleConstants.TYPE_SITE -> synchronizeSiteRole(user, role, shouldHave);
        default -> log.warn("Unsupported type of mapped role " + role.getName());
      }
    }
  }

  private void synchronizeRegularRole(User user, Role role, boolean shouldHave)
      throws PortalException {
    var has = userLocalService.hasRoleUser(role.getRoleId(), user.getUserId());
    if (shouldHave && !has) {
      userLocalService.addRoleUser(role.getRoleId(), user.getUserId());
      logChange("Added", role, user);
    } else if (!shouldHave && has) {
      userLocalService.unsetRoleUsers(role.getRoleId(), new long[] {user.getUserId()});
      logChange("Removed", role, user);
    }
  }

  private void synchronizeSiteRole(User user, Role role, boolean shouldHave) {
    var groupId = jodOhjaajaCmsUtil.getJodOhjaajaCmsGroup().getGroupId();
    var has =
        userGroupRoleLocalService.hasUserGroupRole(user.getUserId(), groupId, role.getRoleId());
    if (shouldHave && !has) {
      userLocalService.addGroupUser(groupId, user.getUserId());
      userGroupRoleLocalService.addUserGroupRoles(
          user.getUserId(), groupId, new long[] {role.getRoleId()});
      logChange("Added", role, user);
    } else if (!shouldHave && has) {
      userGroupRoleLocalService.deleteUserGroupRoles(
          user.getUserId(), groupId, new long[] {role.getRoleId()});
      logChange("Removed", role, user);
    }
  }

  private static void logChange(String change, Role role, User user) {
    log.info(change + " role " + role.getName() + " for user " + user.getUserId());
  }
}
