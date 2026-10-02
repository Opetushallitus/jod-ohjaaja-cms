/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.auth.role;

import com.liferay.asset.categories.admin.web.constants.AssetCategoriesAdminPortletKeys;
import com.liferay.asset.kernel.model.AssetCategory;
import com.liferay.asset.kernel.model.AssetVocabulary;
import com.liferay.document.library.constants.DLPortletKeys;
import com.liferay.document.library.kernel.model.DLFileEntry;
import com.liferay.document.library.kernel.model.DLFolder;
import com.liferay.dynamic.data.mapping.model.DDMStructure;
import com.liferay.dynamic.data.mapping.model.DDMTemplate;
import com.liferay.exportimport.kernel.empty.model.EmptyModelManager;
import com.liferay.journal.constants.JournalConstants;
import com.liferay.journal.constants.JournalPortletKeys;
import com.liferay.journal.model.JournalArticle;
import com.liferay.journal.model.JournalFolder;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.ResourceConstants;
import com.liferay.portal.kernel.model.Role;
import com.liferay.portal.kernel.model.role.RoleConstants;
import com.liferay.portal.kernel.security.permission.ActionKeys;
import com.liferay.portal.kernel.security.permission.ResourceActionsUtil;
import com.liferay.portal.kernel.service.ResourceActionLocalService;
import com.liferay.portal.kernel.service.ResourceLocalService;
import com.liferay.portal.kernel.service.ResourcePermissionLocalService;
import com.liferay.portal.kernel.service.RoleLocalService;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.site.navigation.constants.SiteNavigationConstants;
import com.liferay.site.navigation.model.SiteNavigationMenu;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Creates or takes over the content producer site role and synchronizes its name, titles,
 * description and permissions on every start. Permissions changed in the UI are overwritten.
 */
@Component(immediate = true, service = ContentProducerRoleInitializer.class)
public class ContentProducerRoleInitializer {

  private static final Log log = LogFactoryUtil.getLog(ContentProducerRoleInitializer.class);

  private static final String VIEW_SITE_ADMINISTRATION = "VIEW_SITE_ADMINISTRATION";

  /** Portlets of the Ohjaaja panel category. */
  private static final List<String> OHJAAJA_PORTLETS =
      List.of(
          "fi_okm_jod_ohjaaja_cms_studyprogram_portlet_StudyProgramImporterPortlet",
          "fi_okm_jod_ohjaaja_cms_comments_moderation_portlet_CommentsModerationPortlet",
          "fi_okm_jod_ohjaaja_cms_statistics_portlet_StatisticsPortlet");

  /** Site administration portlets the role can access. */
  private static final List<String> ADMIN_PORTLETS =
      List.of(
          JournalPortletKeys.JOURNAL,
          AssetCategoriesAdminPortletKeys.ASSET_CATEGORIES_ADMIN,
          "com_liferay_site_navigation_admin_web_portlet_SiteNavigationAdminPortlet",
          DLPortletKeys.DOCUMENT_LIBRARY_ADMIN);

  private static final List<String> PORTLET_ACTIONS =
      List.of(ActionKeys.ACCESS_IN_CONTROL_PANEL, ActionKeys.VIEW);

  // Not used directly: RoleLocalService.addRole/updateRole call EmptyModelManagerUtil, which
  // fails if this service is not yet registered. The reference delays activation until it is.
  @Reference private EmptyModelManager emptyModelManager;

  @Reference private Portal portal;
  @Reference private RoleLocalService roleLocalService;
  @Reference private UserLocalService userLocalService;
  @Reference private ResourceActionLocalService resourceActionLocalService;
  @Reference private ResourceLocalService resourceLocalService;
  @Reference private ResourcePermissionLocalService resourcePermissionLocalService;

  @Activate
  protected void activate() {
    try {
      initRole(portal.getDefaultCompanyId());
    } catch (PortalException e) {
      log.error("Initializing role " + ContentProducerRole.NAME + " failed", e);
    }
  }

  /** Creates or updates the role and its permissions. Safe to run repeatedly. */
  public Role initRole(long companyId) throws PortalException {
    var role = findRole(companyId);
    var titleMap = localizedMap(ContentProducerRole.TITLE_FI, ContentProducerRole.TITLE_EN);
    var descriptionMap =
        localizedMap(ContentProducerRole.DESCRIPTION_FI, ContentProducerRole.DESCRIPTION_EN);
    var serviceContext = new ServiceContext();
    serviceContext.setCompanyId(companyId);

    var creatorUserId = getAdminUserId(companyId);

    if (role == null) {
      // The creator must not be the guest user, otherwise Liferay does not add the owner
      // permissions of the role itself and the roles admin UI fails to list the role.
      role =
          roleLocalService.addRole(
              ContentProducerRole.EXTERNAL_REFERENCE_CODE,
              creatorUserId,
              null,
              0,
              ContentProducerRole.NAME,
              titleMap,
              descriptionMap,
              RoleConstants.TYPE_SITE,
              null,
              serviceContext);
      log.info("Created role " + ContentProducerRole.NAME);
    } else if (role.getType() != RoleConstants.TYPE_SITE) {
      log.error(
          "Role " + role.getName() + " is not a site role, leaving it and its permissions as is");
      return role;
    } else if (needsUpdate(role, titleMap, descriptionMap)) {
      role =
          roleLocalService.updateRole(
              ContentProducerRole.EXTERNAL_REFERENCE_CODE,
              role.getRoleId(),
              ContentProducerRole.NAME,
              titleMap,
              descriptionMap,
              role.getSubtype(),
              serviceContext);
      log.info("Updated role " + ContentProducerRole.NAME);
    }

    ensureRoleResources(companyId, creatorUserId, role);
    synchronizePermissions(companyId, role);
    return role;
  }

  private static boolean needsUpdate(
      Role role, Map<Locale, String> titleMap, Map<Locale, String> descriptionMap) {
    return !ContentProducerRole.NAME.equals(role.getName())
        || !ContentProducerRole.EXTERNAL_REFERENCE_CODE.equals(role.getExternalReferenceCode())
        || titleMap.entrySet().stream()
            .anyMatch(e -> !e.getValue().equals(role.getTitleMap().get(e.getKey())))
        || descriptionMap.entrySet().stream()
            .anyMatch(e -> !e.getValue().equals(role.getDescriptionMap().get(e.getKey())));
  }

  /**
   * Adds the individual resource permissions of the role itself if they are missing, for example
   * when the role was created with the guest user as creator.
   */
  private void ensureRoleResources(long companyId, long creatorUserId, Role role)
      throws PortalException {
    var count =
        resourcePermissionLocalService.getResourcePermissionsCount(
            companyId,
            Role.class.getName(),
            ResourceConstants.SCOPE_INDIVIDUAL,
            String.valueOf(role.getRoleId()));
    if (count == 0) {
      resourceLocalService.addResources(
          companyId, 0, creatorUserId, Role.class.getName(), role.getRoleId(), false, false, false);
      log.info("Added missing resource permissions of role " + role.getName());
    }
  }

  private long getAdminUserId(long companyId) throws PortalException {
    var adminRole = roleLocalService.getRole(companyId, RoleConstants.ADMINISTRATOR);
    var adminUsers = userLocalService.getRoleUsers(adminRole.getRoleId(), 0, 1);
    if (adminUsers.isEmpty()) {
      throw new PortalException("No administrator user found in company " + companyId);
    }
    return adminUsers.getFirst().getUserId();
  }

  private Role findRole(long companyId) {
    var role =
        roleLocalService.fetchRoleByExternalReferenceCode(
            ContentProducerRole.EXTERNAL_REFERENCE_CODE, companyId);
    if (role == null) {
      role = roleLocalService.fetchRole(companyId, ContentProducerRole.NAME);
    }
    if (role == null) {
      role = roleLocalService.fetchRole(companyId, ContentProducerRole.LEGACY_NAME);
      if (role != null) {
        log.info(
            "Taking over role "
                + ContentProducerRole.LEGACY_NAME
                + " as "
                + ContentProducerRole.NAME);
      }
    }
    return role;
  }

  private void synchronizePermissions(long companyId, Role role) throws PortalException {
    // Portlet resource actions are registered when the portlet is deployed, which may happen
    // after this component activates. Registering the standard portlet actions up front is
    // harmless, the rest are added when the portlet deploys.
    for (var portletName : portletNames()) {
      resourceActionLocalService.checkResourceActions(portletName, PORTLET_ACTIONS);
    }

    var permissions = getPermissions();
    for (var entry : permissions.entrySet()) {
      var name = entry.getKey();
      var actionIds =
          entry.getValue().stream()
              .filter(
                  actionId -> {
                    var exists = resourceActionLocalService.fetchResourceAction(name, actionId);
                    if (exists == null) {
                      log.warn("Unknown action " + actionId + " for resource " + name);
                    }
                    return exists != null;
                  })
              .toArray(String[]::new);
      resourcePermissionLocalService.setResourcePermissions(
          companyId,
          name,
          ResourceConstants.SCOPE_GROUP_TEMPLATE,
          String.valueOf(0L),
          role.getRoleId(),
          actionIds);
    }

    for (var resourcePermission :
        resourcePermissionLocalService.getRoleResourcePermissions(role.getRoleId())) {
      var managed =
          resourcePermission.getScope() == ResourceConstants.SCOPE_GROUP_TEMPLATE
              && permissions.containsKey(resourcePermission.getName());
      if (!managed) {
        log.info(
            "Removing permissions of role "
                + role.getName()
                + " on "
                + resourcePermission.getName()
                + " (scope "
                + resourcePermission.getScope()
                + ", primKey "
                + resourcePermission.getPrimKey()
                + ")");
        resourcePermissionLocalService.deleteResourcePermission(resourcePermission);
      }
    }
  }

  /** Resource permissions of the role (group template scope), keyed by resource name. */
  public static Map<String, List<String>> getPermissions() {
    var permissions = new LinkedHashMap<String, List<String>>();

    // Site administration menu
    permissions.put(Group.class.getName(), List.of(ActionKeys.VIEW, VIEW_SITE_ADMINISTRATION));

    // Web content
    permissions.put(
        JournalConstants.RESOURCE_NAME,
        List.of(ActionKeys.ADD_ARTICLE, ActionKeys.ADD_FOLDER, ActionKeys.VIEW));
    permissions.put(
        JournalArticle.class.getName(),
        List.of(
            ActionKeys.VIEW,
            ActionKeys.UPDATE,
            ActionKeys.DELETE,
            ActionKeys.EXPIRE,
            ActionKeys.ADD_DISCUSSION));
    permissions.put(
        JournalFolder.class.getName(),
        List.of(
            ActionKeys.VIEW,
            ActionKeys.UPDATE,
            ActionKeys.ADD_ARTICLE,
            ActionKeys.ADD_SUBFOLDER,
            ActionKeys.DELETE));
    permissions.put(
        ResourceActionsUtil.getCompositeModelName(
            DDMStructure.class.getName(), JournalArticle.class.getName()),
        List.of(ActionKeys.VIEW));
    permissions.put(
        ResourceActionsUtil.getCompositeModelName(
            DDMTemplate.class.getName(), JournalArticle.class.getName()),
        List.of(ActionKeys.VIEW));

    // Categories
    permissions.put("com.liferay.asset.categories", List.of(ActionKeys.ADD_CATEGORY));
    permissions.put(
        AssetCategory.class.getName(),
        List.of(ActionKeys.VIEW, ActionKeys.UPDATE, ActionKeys.ADD_CATEGORY, ActionKeys.DELETE));
    permissions.put(AssetVocabulary.class.getName(), List.of(ActionKeys.VIEW));

    // Navigation
    permissions.put(SiteNavigationConstants.RESOURCE_NAME, List.of("ADD_SITE_NAVIGATION_MENU"));
    permissions.put(
        SiteNavigationMenu.class.getName(), List.of(ActionKeys.VIEW, ActionKeys.UPDATE));

    // Documents
    permissions.put(
        "com.liferay.document.library",
        List.of(ActionKeys.ADD_DOCUMENT, ActionKeys.ADD_FOLDER, ActionKeys.VIEW));
    permissions.put(
        DLFileEntry.class.getName(),
        List.of(ActionKeys.VIEW, ActionKeys.UPDATE, ActionKeys.DELETE));
    permissions.put(
        DLFolder.class.getName(),
        List.of(
            ActionKeys.VIEW, ActionKeys.ADD_DOCUMENT, ActionKeys.ADD_SUBFOLDER, ActionKeys.UPDATE));

    // Portlets
    for (var portletName : portletNames()) {
      permissions.put(portletName, PORTLET_ACTIONS);
    }

    return Map.copyOf(permissions);
  }

  private static List<String> portletNames() {
    return Stream.concat(ADMIN_PORTLETS.stream(), OHJAAJA_PORTLETS.stream()).toList();
  }

  private static Map<Locale, String> localizedMap(String finnish, String english) {
    return Map.of(LocaleUtil.fromLanguageId("fi_FI"), finnish, LocaleUtil.US, english);
  }
}
