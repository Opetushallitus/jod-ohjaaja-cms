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
import com.liferay.expando.kernel.model.ExpandoColumnConstants;
import com.liferay.expando.kernel.util.ExpandoBridgeFactoryUtil;
import com.liferay.journal.model.JournalArticle;
import com.liferay.journal.model.JournalArticleResource;
import com.liferay.journal.service.JournalArticleResourceLocalService;
import com.liferay.journal.service.JournalArticleService;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.security.auth.GuestOrUserUtil;
import com.liferay.portal.kernel.security.permission.PermissionCheckerFactoryUtil;
import com.liferay.portal.kernel.security.permission.PermissionThreadLocal;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.UnicodeProperties;
import com.liferay.portal.kernel.util.UnicodePropertiesBuilder;
import com.liferay.portal.security.service.access.policy.service.SAPEntryLocalService;
import com.liferay.portal.vulcan.util.LocalizedMapUtil;
import com.liferay.site.navigation.model.SiteNavigationMenu;
import com.liferay.site.navigation.model.SiteNavigationMenuItem;
import com.liferay.site.navigation.service.SiteNavigationMenuItemService;
import com.liferay.site.navigation.service.SiteNavigationMenuService;
import com.liferay.site.navigation.util.comparator.SiteNavigationMenuItemOrderComparator;
import fi.okm.jod.ohjaaja.cms.navigation.dto.NavigationDto;
import fi.okm.jod.ohjaaja.cms.navigation.dto.NavigationItemDto;
import fi.okm.jod.ohjaaja.cms.navigation.rest.application.NavigationRestApplication;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(immediate = true, service = NavigationService.class)
public class NavigationServiceImpl implements NavigationService {

  private static final String ASSET_CATEGORY_CLASS_NAME =
      "com.liferay.asset.kernel.model.AssetCategory";
  private static final String JOURNAL_ARTICLE_CLASS_NAME =
      "com.liferay.journal.model.JournalArticle";

  private static final String SAP_ENTRY_NAME = "JOD_OHJAAJA_NAVIGATION";
  private static final Map<Locale, String> SAP_DESCRIPTION =
      Map.of(LocaleUtil.fromLanguageId("en_US"), "Public access JOD OHJAAJA NAVIGATION");
  private static final String SERVICE_SIGNATURE = NavigationRestApplication.class.getName() + "#*";

  private static final String CUSTOM_FIELD_NAME = "Type";
  private static final String[] CUSTOM_FIELD_DEFAULT_DATA =
      new String[] {"Article", "CategoryListing", "CategoryMain"};

  private static final Log log = LogFactoryUtil.getLog(NavigationServiceImpl.class);

  @Reference private SiteNavigationMenuItemService siteNavigationMenuItemService;

  @Reference private SiteNavigationMenuService siteNavigationMenuService;

  @Reference private AssetCategoryService assetCategoryService;

  @Reference private JournalArticleService journalArticleService;

  @Reference private JournalArticleResourceLocalService journalArticleResourceLocalService;

  @Reference private SAPEntryLocalService sapEntryLocalService;

  public NavigationDto getNavigation(Long siteId, String languageId) {
    var siteNavigationMenus = siteNavigationMenuService.getSiteNavigationMenus(20117);

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
    initCustomField();
  }

  private NavigationDto toNavigationDto(SiteNavigationMenu siteNavigationMenu, String languageId) {
    var siteNavigationMenuItemsMap =
        getSiteNavigationMenuItemsMap(
            siteNavigationMenuItemService.getSiteNavigationMenuItems(
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

  private String getType(
      SiteNavigationMenuItem siteNavigationMenuItem,
      SiteNavigationMenuItemType siteNavigationMenuItemType) {
    return switch (siteNavigationMenuItemType) {
      case ASSET_CATEGORY -> {
        var expandoBridge = siteNavigationMenuItem.getExpandoBridge();
        var attributes = expandoBridge.getAttributes(false);

        yield attributes.containsKey(CUSTOM_FIELD_NAME)
                && attributes.get(CUSTOM_FIELD_NAME) instanceof String[] customFieldData
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

  private void initCustomField() {
    try {
      var checker =
          PermissionCheckerFactoryUtil.create(
              GuestOrUserUtil.getGuestOrUser(PortalUtil.getDefaultCompanyId()));
      PermissionThreadLocal.setPermissionChecker(checker);
      var expandoBridge =
          ExpandoBridgeFactoryUtil.getExpandoBridge(
              PortalUtil.getDefaultCompanyId(), SiteNavigationMenuItem.class.getName());

      if (expandoBridge.getAttributes().containsKey(CUSTOM_FIELD_NAME)) {
        log.info("Custom field already exists");
      } else {
        expandoBridge.addAttribute(
            CUSTOM_FIELD_NAME,
            ExpandoColumnConstants.STRING_ARRAY,
            CUSTOM_FIELD_DEFAULT_DATA,
            false);
        var unicodeProperties = getUnicodeProperties();
        expandoBridge.setAttributeProperties(CUSTOM_FIELD_NAME, unicodeProperties, false);
        log.info("Added custom field");
      }
    } catch (PortalException e) {
      throw new RuntimeException(e);
    }
  }

  private static UnicodeProperties getUnicodeProperties() {
    var unicodeProperties = new UnicodeProperties();
    unicodeProperties.setProperty(ExpandoColumnConstants.PROPERTY_HIDDEN, "false");
    unicodeProperties.setProperty(
        ExpandoColumnConstants.PROPERTY_DISPLAY_TYPE,
        ExpandoColumnConstants.PROPERTY_DISPLAY_TYPE_SELECTION_LIST);
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
}
