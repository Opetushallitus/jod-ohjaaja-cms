/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.navigation.service;

import com.liferay.asset.kernel.model.AssetCategory;
import com.liferay.asset.kernel.service.AssetCategoryService;
import com.liferay.expando.kernel.model.ExpandoBridge;
import com.liferay.expando.kernel.model.ExpandoColumn;
import com.liferay.expando.kernel.model.ExpandoColumnConstants;
import com.liferay.expando.kernel.model.ExpandoTableConstants;
import com.liferay.expando.kernel.service.ExpandoColumnLocalServiceUtil;
import com.liferay.expando.kernel.util.ExpandoBridgeFactoryUtil;
import com.liferay.journal.model.JournalArticle;
import com.liferay.journal.model.JournalArticleResource;
import com.liferay.journal.service.JournalArticleResourceLocalService;
import com.liferay.journal.service.JournalArticleService;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.ResourceConstants;
import com.liferay.portal.kernel.model.role.RoleConstants;
import com.liferay.portal.kernel.security.auth.GuestOrUserUtil;
import com.liferay.portal.kernel.security.permission.ActionKeys;
import com.liferay.portal.kernel.security.permission.PermissionCheckerFactoryUtil;
import com.liferay.portal.kernel.security.permission.PermissionThreadLocal;
import com.liferay.portal.kernel.security.permission.ResourceActionsUtil;
import com.liferay.portal.kernel.service.ResourcePermissionLocalServiceUtil;
import com.liferay.portal.kernel.service.RoleLocalServiceUtil;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.UserLocalServiceUtil;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.UnicodeProperties;
import com.liferay.portal.kernel.util.UnicodePropertiesBuilder;
import com.liferay.portal.language.override.service.PLOEntryLocalService;
import com.liferay.portal.security.service.access.policy.service.SAPEntryLocalService;
import com.liferay.portal.vulcan.util.LocalizedMapUtil;
import com.liferay.site.navigation.model.SiteNavigationMenu;
import com.liferay.site.navigation.model.SiteNavigationMenuItem;
import com.liferay.site.navigation.service.SiteNavigationMenuItemLocalService;
import com.liferay.site.navigation.service.SiteNavigationMenuService;
import com.liferay.site.navigation.util.comparator.SiteNavigationMenuItemOrderComparator;
import fi.okm.jod.ohjaaja.cms.navigation.dto.NavigationDto;
import fi.okm.jod.ohjaaja.cms.navigation.dto.NavigationItemDto;
import fi.okm.jod.ohjaaja.cms.navigation.exception.MultipleStudyProgramListingMenuItemExpection;
import fi.okm.jod.ohjaaja.cms.navigation.exception.StudyProgramListingMissingException;
import fi.okm.jod.ohjaaja.cms.navigation.rest.application.NavigationRestApplication;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(immediate = true, service = NavigationService.class)
public class NavigationServiceImpl implements NavigationService {

  private static final Long GROUP_ID = 20117L; // JOD OHJAAJA group ID

  private static final String ASSET_CATEGORY_CLASS_NAME =
      "com.liferay.asset.kernel.model.AssetCategory";
  private static final String JOURNAL_ARTICLE_CLASS_NAME =
      "com.liferay.journal.model.JournalArticle";

  private static final String SAP_ENTRY_NAME = "JOD_OHJAAJA_NAVIGATION";
  private static final Map<Locale, String> SAP_DESCRIPTION =
      Map.of(LocaleUtil.fromLanguageId("en_US"), "Public access JOD OHJAAJA NAVIGATION");
  private static final String SERVICE_SIGNATURE = NavigationRestApplication.class.getName() + "#*";

  private static final String TYPE_CUSTOM_FIELD_NAME = "Type";
  private static final String HIDE_FROM_HOME_PAGE_NEWEST_CAROUSEL_FIELD_NAME =
      "hideFromHomePageNewestCarousel";
  private static final String HIDE_FROM_HOME_PAGE_MOST_VIEWED_CAROUSEL_FIELD_NAME =
      "hideFromHomePageMostViewedCarousel";
  private static final String HIDE_FROM_MAIN_CATEGORY_PAGE_NEWEST_CAROUSEL_FIELD_NAME =
      "hideFromMainCategoryPageNewestCarousel";
  private static final String HIDE_FROM_MAIN_CATEGORY_PAGE_MOST_VIEWED_CAROUSEL_FIELD_NAME =
      "hideFromMainCategoryPageMostViewedCarousel";

  private static final CustomFieldData[] CUSTOM_FIELDS =
      new CustomFieldData[] {
        new CustomFieldData(
            TYPE_CUSTOM_FIELD_NAME,
            ExpandoColumnConstants.STRING_ARRAY,
            new String[] {"Article", "CategoryListing", "CategoryMain", "StudyProgramsListing"},
            ExpandoColumnConstants.PROPERTY_DISPLAY_TYPE_SELECTION_LIST,
            Map.of("fi_FI", "Tyyppi", "en_US", "Type")),
        new CustomFieldData(
            HIDE_FROM_HOME_PAGE_NEWEST_CAROUSEL_FIELD_NAME,
            ExpandoColumnConstants.BOOLEAN,
            false,
            ExpandoColumnConstants.PROPERTY_DISPLAY_TYPE_CHECKBOX,
            Map.of(
                "fi_FI",
                "Piilota uusien karusellista etusivulta",
                "en_US",
                "Hide from the homepage new items carousel")),
        new CustomFieldData(
            HIDE_FROM_HOME_PAGE_MOST_VIEWED_CAROUSEL_FIELD_NAME,
            ExpandoColumnConstants.BOOLEAN,
            false,
            ExpandoColumnConstants.PROPERTY_DISPLAY_TYPE_CHECKBOX,
            Map.of(
                "fi_FI",
                "Piilota suosittujen karusellista etusivulta",
                "en_US",
                "Hide from the homepage most viewed carousel")),
        new CustomFieldData(
            HIDE_FROM_MAIN_CATEGORY_PAGE_NEWEST_CAROUSEL_FIELD_NAME,
            ExpandoColumnConstants.BOOLEAN,
            false,
            ExpandoColumnConstants.PROPERTY_DISPLAY_TYPE_CHECKBOX,
            Map.of(
                "fi_FI",
                "Piilota uusien karusellista pääkategoriasivulta",
                "en_US",
                "Hide from the main category page new items carousel")),
        new CustomFieldData(
            HIDE_FROM_MAIN_CATEGORY_PAGE_MOST_VIEWED_CAROUSEL_FIELD_NAME,
            ExpandoColumnConstants.BOOLEAN,
            false,
            ExpandoColumnConstants.PROPERTY_DISPLAY_TYPE_CHECKBOX,
            Map.of(
                "fi_FI",
                "Piilota suosittujen karusellista pääkategoriasivulta",
                "en_US",
                "Hide from the main category page most viewed carousel"))
      };

  private static final Log log = LogFactoryUtil.getLog(NavigationServiceImpl.class);

  @Reference private SiteNavigationMenuItemLocalService siteNavigationMenuItemLocalService;
  @Reference private SiteNavigationMenuService siteNavigationMenuService;
  @Reference private AssetCategoryService assetCategoryService;
  @Reference private JournalArticleService journalArticleService;
  @Reference private JournalArticleResourceLocalService journalArticleResourceLocalService;
  @Reference private SAPEntryLocalService sapEntryLocalService;
  @Reference private PLOEntryLocalService ploEntryLocalService;

  @Override
  public NavigationDto getNavigation(Long siteId, String languageId) {
    var siteNavigationMenus = siteNavigationMenuService.getSiteNavigationMenus(GROUP_ID);

    if (!siteNavigationMenus.isEmpty()) {
      return toNavigationDto(siteNavigationMenus.getFirst(), languageId);
    }

    return null;
  }

  @Override
  public void initNavigation() {
    try {
      sapEntryLocalService.getSAPEntry(PortalUtil.getDefaultCompanyId(), SAP_ENTRY_NAME);
    } catch (PortalException portalException) {
      initServiceAccessPolicy();
    }
    initCustomFields();
  }

  @Override
  public void addOrUpdateStudyProgramNavigationMenuItem(
      JournalArticle studyProgramJournalArticle, ServiceContext serviceContext)
      throws StudyProgramListingMissingException, MultipleStudyProgramListingMenuItemExpection {
    var studyProgramsParentMenuItem = getStudyProgramsParentMenuItem();

    try {
      siteNavigationMenuItemLocalService.addOrUpdateSiteNavigationMenuItem(
          studyProgramJournalArticle.getExternalReferenceCode(),
          serviceContext.getUserId(),
          GROUP_ID,
          studyProgramsParentMenuItem.getSiteNavigationMenuId(),
          studyProgramsParentMenuItem.getSiteNavigationMenuItemId(),
          JournalArticle.class.getName(),
          UnicodePropertiesBuilder.create(true)
              .put("classNameId", String.valueOf(PortalUtil.getClassNameId(JournalArticle.class)))
              .put("classPK", String.valueOf(studyProgramJournalArticle.getResourcePrimKey()))
              .put("classTypeId", String.valueOf(studyProgramJournalArticle.getDDMStructureId()))
              .put("title", String.valueOf(studyProgramJournalArticle.getTitle()))
              .put(
                  "type",
                  ResourceActionsUtil.getModelResource(
                      LocaleUtil.getDefault(), JournalArticle.class.getName()))
              .put("useCustomName", false)
              .buildString(),
          serviceContext);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void deleteStudyProgramNavigationMenuItem(String externalReferenceCode)
      throws PortalException {

    siteNavigationMenuItemLocalService.deleteSiteNavigationMenuItem(
        externalReferenceCode, GROUP_ID);
  }

  public SiteNavigationMenuItem getStudyProgramsParentMenuItem()
      throws StudyProgramListingMissingException, MultipleStudyProgramListingMenuItemExpection {
    var siteNavigationMenus = siteNavigationMenuService.getSiteNavigationMenus(GROUP_ID);
    if (siteNavigationMenus.isEmpty()) {
      throw new StudyProgramListingMissingException("No site navigation menus found");
    }
    var siteNavigationMenu = siteNavigationMenus.getFirst();
    var menuItems =
        siteNavigationMenuItemLocalService.getSiteNavigationMenuItems(
            siteNavigationMenu.getSiteNavigationMenuId(),
            SiteNavigationMenuItemOrderComparator.getInstance(true));

    var studyProgramsListingMenuItems =
        menuItems.stream()
            .filter(menuItem -> "StudyProgramsListing".equals(getTypeCustomFieldValue(menuItem)))
            .toList();
    if (studyProgramsListingMenuItems.isEmpty()) {
      throw new StudyProgramListingMissingException(
          "No StudyProgramsListing parent menu item found");
    } else if (studyProgramsListingMenuItems.size() > 1) {
      throw new MultipleStudyProgramListingMenuItemExpection(
          "Multiple StudyProgramsListing parent menu item found");
    }
    return studyProgramsListingMenuItems.getFirst();
  }

  private String getTypeCustomFieldValue(SiteNavigationMenuItem siteNavigationMenuItem) {
    var expandoBridge = siteNavigationMenuItem.getExpandoBridge();
    var attributes = expandoBridge.getAttributes(false);
    if (attributes.containsKey(TYPE_CUSTOM_FIELD_NAME)
        && attributes.get(TYPE_CUSTOM_FIELD_NAME) instanceof String[] customFieldData
        && customFieldData.length > 0) {
      return customFieldData[0];
    }
    return null;
  }

  private NavigationDto toNavigationDto(SiteNavigationMenu siteNavigationMenu, String languageId) {
    var siteNavigationMenuItemsMap =
        getSiteNavigationMenuItemsMap(
            siteNavigationMenuItemLocalService.getSiteNavigationMenuItems(
                siteNavigationMenu.getSiteNavigationMenuId(),
                SiteNavigationMenuItemOrderComparator.getInstance(true)));

    return new NavigationDto(
        siteNavigationMenu.getSiteNavigationMenuId(),
        siteNavigationMenu.getGroupId(),
        siteNavigationMenuItemsMap.getOrDefault(0L, Collections.emptyList()).stream()
            .map(
                siteNavigationMenuItem ->
                    toNavigationItemDto(
                        siteNavigationMenuItem, languageId, siteNavigationMenuItemsMap))
            .collect(Collectors.toList()));
  }

  private Map<Long, List<SiteNavigationMenuItem>> getSiteNavigationMenuItemsMap(
      List<SiteNavigationMenuItem> siteNavigationMenuItems) {

    return siteNavigationMenuItems.stream()
        .collect(Collectors.groupingBy(SiteNavigationMenuItem::getParentSiteNavigationMenuItemId));
  }

  private NavigationItemDto toNavigationItemDto(
      SiteNavigationMenuItem siteNavigationMenuItem,
      String languageId,
      Map<Long, List<SiteNavigationMenuItem>> siteNavigationMenuItemsMap) {

    var type = getSiteNavigationMenuItemType(siteNavigationMenuItem);
    var unicodeProperties = getUnicodeProperties(siteNavigationMenuItem);
    var assetCategory = getAssetCategory(type, unicodeProperties);
    var journalArticle = getJournalArticle(type, unicodeProperties);
    var name = getName(assetCategory, journalArticle, type, unicodeProperties, languageId);
    var nameI18n = getNameI18n(assetCategory, journalArticle, type);
    var description = getDescription(assetCategory, journalArticle, type, languageId);
    var descriptionI18n = getDescriptionI18n(assetCategory, journalArticle, type);

    return new NavigationItemDto(
        siteNavigationMenuItem.getSiteNavigationMenuItemId(),
        name,
        nameI18n,
        description,
        descriptionI18n,
        getType(siteNavigationMenuItem, type),
        getBooleanCustomFieldValue(
            siteNavigationMenuItem, HIDE_FROM_HOME_PAGE_NEWEST_CAROUSEL_FIELD_NAME),
        getBooleanCustomFieldValue(
            siteNavigationMenuItem, HIDE_FROM_HOME_PAGE_MOST_VIEWED_CAROUSEL_FIELD_NAME),
        getBooleanCustomFieldValue(
            siteNavigationMenuItem, HIDE_FROM_MAIN_CATEGORY_PAGE_NEWEST_CAROUSEL_FIELD_NAME),
        getBooleanCustomFieldValue(
            siteNavigationMenuItem, HIDE_FROM_MAIN_CATEGORY_PAGE_MOST_VIEWED_CAROUSEL_FIELD_NAME),
        assetCategory != null
            ? assetCategory.getExternalReferenceCode()
            : (journalArticle != null)
                ? journalArticle.getExternalReferenceCode()
                : siteNavigationMenuItem.getExternalReferenceCode(),
        (journalArticle != null) ? journalArticle.getResourcePrimKey() : null,
        (assetCategory != null) ? assetCategory.getCategoryId() : null,
        siteNavigationMenuItemsMap
            .getOrDefault(
                siteNavigationMenuItem.getSiteNavigationMenuItemId(), Collections.emptyList())
            .stream()
            .map(
                childSiteNavigationMenuItem ->
                    toNavigationItemDto(
                        childSiteNavigationMenuItem, languageId, siteNavigationMenuItemsMap))
            .collect(Collectors.toList()),
        siteNavigationMenuItem.getParentSiteNavigationMenuItemId());
  }

  private SiteNavigationMenuItemType getSiteNavigationMenuItemType(
      SiteNavigationMenuItem siteNavigationMenuItem) {
    return switch (siteNavigationMenuItem.getType()) {
      case ASSET_CATEGORY_CLASS_NAME -> SiteNavigationMenuItemType.ASSET_CATEGORY;
      case JOURNAL_ARTICLE_CLASS_NAME -> SiteNavigationMenuItemType.JOURNAL_ARTICLE;
      default -> SiteNavigationMenuItemType.NOT_RELEVANT;
    };
  }

  private UnicodeProperties getUnicodeProperties(SiteNavigationMenuItem siteNavigationMenuItem) {
    if (siteNavigationMenuItem == null) {
      return null;
    }
    return UnicodePropertiesBuilder.fastLoad(siteNavigationMenuItem.getTypeSettings()).build();
  }

  private AssetCategory getAssetCategory(
      SiteNavigationMenuItemType type, UnicodeProperties unicodeProperties) {
    if (type != SiteNavigationMenuItemType.ASSET_CATEGORY) {
      return null;
    }
    try {
      return assetCategoryService.fetchCategory(Long.parseLong(unicodeProperties.get("classPK")));
    } catch (PortalException e) {
      return null;
    }
  }

  private JournalArticle getJournalArticle(
      SiteNavigationMenuItemType type, UnicodeProperties unicodeProperties) {
    if (type != SiteNavigationMenuItemType.JOURNAL_ARTICLE) {
      return null;
    }
    try {
      var journalArticleResourcePrimaryKey = Long.parseLong(unicodeProperties.get("classPK"));
      JournalArticleResource journalArticleResource =
          journalArticleResourceLocalService.getArticleResource(journalArticleResourcePrimaryKey);
      return journalArticleService.getArticle(journalArticleResource.getLatestArticlePK());
    } catch (PortalException e) {
      return null;
    }
  }

  private String getName(
      AssetCategory category,
      JournalArticle article,
      SiteNavigationMenuItemType type,
      UnicodeProperties unicodeProperties,
      String languageId) {

    return switch (type) {
      case ASSET_CATEGORY ->
          (category != null)
              ? category.getTitle(languageId)
              : unicodeProperties.getProperty("title");
      case JOURNAL_ARTICLE ->
          (article != null) ? article.getTitle(languageId) : unicodeProperties.getProperty("title");
      default -> unicodeProperties.getProperty("title");
    };
  }

  private Map<String, String> getNameI18n(
      AssetCategory category, JournalArticle article, SiteNavigationMenuItemType type) {

    return switch (type) {
      case ASSET_CATEGORY ->
          (category != null) ? LocalizedMapUtil.getI18nMap(category.getTitleMap()) : Map.of();
      case JOURNAL_ARTICLE ->
          (article != null) ? LocalizedMapUtil.getI18nMap(article.getTitleMap()) : Map.of();
      default -> Map.of();
    };
  }

  private String getDescription(
      AssetCategory category,
      JournalArticle article,
      SiteNavigationMenuItemType type,
      String languageId) {

    return switch (type) {
      case ASSET_CATEGORY -> (category != null) ? category.getDescription(languageId) : "";
      case JOURNAL_ARTICLE -> (article != null) ? article.getDescription(languageId) : "";
      default -> "";
    };
  }

  private Map<String, String> getDescriptionI18n(
      AssetCategory category, JournalArticle article, SiteNavigationMenuItemType type) {

    return switch (type) {
      case ASSET_CATEGORY ->
          (category != null) ? LocalizedMapUtil.getI18nMap(category.getDescriptionMap()) : Map.of();
      case JOURNAL_ARTICLE ->
          (article != null) ? LocalizedMapUtil.getI18nMap(article.getDescriptionMap()) : Map.of();
      default -> Map.of();
    };
  }

  private boolean getBooleanCustomFieldValue(
      SiteNavigationMenuItem siteNavigationMenuItem, String fieldName) {
    var expandoBridge = siteNavigationMenuItem.getExpandoBridge();
    var attributes = expandoBridge.getAttributes(false);
    if (attributes.containsKey(fieldName) && attributes.get(fieldName) instanceof Boolean value) {
      return value;
    }
    return false;
  }

  private String getType(
      SiteNavigationMenuItem siteNavigationMenuItem,
      SiteNavigationMenuItemType siteNavigationMenuItemType) {
    return switch (siteNavigationMenuItemType) {
      case ASSET_CATEGORY -> {
        var expandoBridge = siteNavigationMenuItem.getExpandoBridge();
        var attributes = expandoBridge.getAttributes(false);

        yield attributes.containsKey(TYPE_CUSTOM_FIELD_NAME)
                && attributes.get(TYPE_CUSTOM_FIELD_NAME) instanceof String[] customFieldData
                && customFieldData.length > 0
            ? customFieldData[0]
            : "CategoryListing";
      }
      case JOURNAL_ARTICLE -> "Article";
      default -> siteNavigationMenuItem.getType();
    };
  }

  private void initServiceAccessPolicy() {
    try {
      sapEntryLocalService.addSAPEntry(
          GuestOrUserUtil.getGuestOrUser(PortalUtil.getDefaultCompanyId()).getUserId(),
          SERVICE_SIGNATURE,
          true,
          true,
          SAP_ENTRY_NAME,
          SAP_DESCRIPTION,
          new ServiceContext());
    } catch (PortalException e) {
      throw new RuntimeException(e);
    }
  }

  private void initCustomFields() {
    try {
      var role =
          RoleLocalServiceUtil.fetchRole(
              PortalUtil.getDefaultCompanyId(), RoleConstants.ADMINISTRATOR);
      var adminUsers = UserLocalServiceUtil.getRoleUsers(role.getRoleId(), 0, 1);
      if (adminUsers.isEmpty()) {
        log.warn("No admin users found, using guest user for permission checker");
      }
      var user =
          adminUsers.isEmpty()
              ? UserLocalServiceUtil.getGuestUser(PortalUtil.getDefaultCompanyId())
              : adminUsers.getFirst();
      var checker = PermissionCheckerFactoryUtil.create(user);
      PermissionThreadLocal.setPermissionChecker(checker);
      var expandoBridge =
          ExpandoBridgeFactoryUtil.getExpandoBridge(
              PortalUtil.getDefaultCompanyId(), SiteNavigationMenuItem.class.getName());

      for (var customFieldData : CUSTOM_FIELDS) {
        initCustomField(
            expandoBridge,
            customFieldData.name(),
            customFieldData.type(),
            customFieldData.defaultValue(),
            customFieldData.propertyDisplayType());
        grantUserPermissionsForCustomField(customFieldData.name());

        customFieldData.localizationMap.forEach(
            (locale, name) -> {
              try {
                ploEntryLocalService.addOrUpdatePLOEntry(
                    PortalUtil.getDefaultCompanyId(),
                    user.getUserId(),
                    customFieldData.name(),
                    locale,
                    name);
              } catch (PortalException e) {
                log.error(
                    "Failed to add or update PLO entry for custom field "
                        + customFieldData.name()
                        + " and locale "
                        + locale,
                    e);
              }
            });
      }

    } catch (PortalException e) {
      throw new RuntimeException(e);
    }
  }

  private void initCustomField(
      ExpandoBridge expandoBridge,
      String customFieldName,
      int customFieldType,
      Serializable customFieldDefaultData,
      String propertyDisplayType)
      throws PortalException {
    if (expandoBridge.getAttributes().containsKey(customFieldName)) {
      log.info("Custom field " + customFieldName + " already exists");
      expandoBridge.setAttributeDefault(customFieldName, customFieldDefaultData);
    } else {
      expandoBridge.addAttribute(customFieldName, customFieldType, customFieldDefaultData, false);
      var unicodeProperties = getUnicodeProperties(propertyDisplayType);
      expandoBridge.setAttributeProperties(customFieldName, unicodeProperties, false);
      log.info("Added custom field " + customFieldName);
    }
  }

  private void grantUserPermissionsForCustomField(String customFieldName) throws PortalException {
    var companyId = PortalUtil.getDefaultCompanyId();
    var userRole = RoleLocalServiceUtil.fetchRole(companyId, RoleConstants.USER);

    if (userRole == null) {
      log.warn("User role not found, cannot grant custom field permissions for " + customFieldName);
      return;
    }

    var expandoColumn =
        ExpandoColumnLocalServiceUtil.getColumn(
            companyId,
            SiteNavigationMenuItem.class.getName(),
            ExpandoTableConstants.DEFAULT_TABLE_NAME,
            customFieldName);

    ResourcePermissionLocalServiceUtil.setResourcePermissions(
        companyId,
        ExpandoColumn.class.getName(),
        ResourceConstants.SCOPE_INDIVIDUAL,
        String.valueOf(expandoColumn.getColumnId()),
        userRole.getRoleId(),
        new String[] {ActionKeys.VIEW, ActionKeys.UPDATE});
  }

  private static UnicodeProperties getUnicodeProperties(String propertyDisplayType) {
    var unicodeProperties = new UnicodeProperties();
    unicodeProperties.setProperty(ExpandoColumnConstants.PROPERTY_HIDDEN, "false");
    unicodeProperties.setProperty(
        ExpandoColumnConstants.PROPERTY_DISPLAY_TYPE, propertyDisplayType);
    unicodeProperties.setProperty(
        ExpandoColumnConstants.INDEX_TYPE,
        String.valueOf(ExpandoColumnConstants.INDEX_TYPE_KEYWORD));
    unicodeProperties.setProperty(ExpandoColumnConstants.PROPERTY_LOCALIZE_FIELD_NAME, "true");
    unicodeProperties.setProperty(
        ExpandoColumnConstants.PROPERTY_VISIBLE_WITH_UPDATE_PERMISSION, "false");
    return unicodeProperties;
  }

  private enum SiteNavigationMenuItemType {
    ASSET_CATEGORY,
    JOURNAL_ARTICLE,
    NOT_RELEVANT
  }

  private record CustomFieldData(
      String name,
      int type,
      Serializable defaultValue,
      String propertyDisplayType,
      Map<String, String> localizationMap) {}
}
