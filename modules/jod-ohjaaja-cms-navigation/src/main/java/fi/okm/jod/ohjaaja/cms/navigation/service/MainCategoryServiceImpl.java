/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.navigation.service;

import com.liferay.asset.kernel.model.AssetCategory;
import com.liferay.asset.kernel.model.AssetVocabulary;
import com.liferay.asset.kernel.service.AssetCategoryLocalService;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.UnicodePropertiesBuilder;
import com.liferay.portal.vulcan.util.LocalizedMapUtil;
import com.liferay.site.navigation.service.SiteNavigationMenuItemLocalService;
import com.liferay.site.navigation.service.SiteNavigationMenuLocalService;
import fi.okm.jod.ohjaaja.cms.util.AdminUtil;
import fi.okm.jod.ohjaaja.cms.util.JodOhjaajaCmsUtil;
import fi.okm.jod.ohjaaja.cms.util.TakeOverUtil;
import fi.okm.jod.ohjaaja.cms.util.VocabularyInitializer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(immediate = true, service = MainCategoryService.class)
public class MainCategoryServiceImpl implements MainCategoryService {

  private static final Log log = LogFactoryUtil.getLog(MainCategoryServiceImpl.class);

  private static final String PROPERTY_EXTERNAL_REFERENCE_CODE = "externalReferenceCode";

  private static final String LANGUAGE_ID_FI_FI = "fi_FI";
  private static final String LANGUAGE_ID_EN_US = "en_US";
  private static final String LANGUAGE_ID_SV_SE = "sv_SE";

  // Created by the study program importer before the vocabulary was managed here
  private static final String LEGACY_VOCABULARY_EXTERNAL_REFERENCE_CODE =
      "ohjaaja-study-program-category-vocabulary";
  private static final String VOCABULARY_NAME = "category";
  private static final Map<String, String> VOCABULARY_TITLES =
      Map.of(
          LANGUAGE_ID_FI_FI,
          "Kategoria",
          LANGUAGE_ID_EN_US,
          "Category",
          LANGUAGE_ID_SV_SE,
          "Kategori");

  // In the order of the navigation. The titles are those of jod-ohjaaja-ui.
  private static final List<MainCategory> MAIN_CATEGORIES =
      List.of(
          new MainCategory(
              INFORMATION_CONTENT_CATEGORY_EXTERNAL_REFERENCE_CODE,
              null,
              Map.of(
                  LANGUAGE_ID_FI_FI, "Ohjaajan tietosisällöt",
                  LANGUAGE_ID_EN_US, "Guidance practitioner's information content",
                  LANGUAGE_ID_SV_SE, "Vägledarens informationsinnehåll")),
          new MainCategory(
              CUSTOMER_WORK_CATEGORY_EXTERNAL_REFERENCE_CODE,
              null,
              Map.of(
                  LANGUAGE_ID_FI_FI, "Asiakastyön tueksi",
                  LANGUAGE_ID_EN_US, "To support customer work",
                  LANGUAGE_ID_SV_SE, "Till stöd för kundarbetet")),
          new MainCategory(
              PROFESSIONAL_DEVELOPMENT_CATEGORY_EXTERNAL_REFERENCE_CODE,
              // Created by the study program importer
              "ohjaaja-study-program-parent-category",
              Map.of(
                  LANGUAGE_ID_FI_FI, "Ammatillinen kehittyminen",
                  LANGUAGE_ID_EN_US, "Professional development",
                  LANGUAGE_ID_SV_SE, "Yrkesmässig utveckling")));

  @Reference private AssetCategoryLocalService assetCategoryLocalService;
  @Reference private SiteNavigationMenuLocalService siteNavigationMenuLocalService;
  @Reference private SiteNavigationMenuItemLocalService siteNavigationMenuItemLocalService;
  @Reference private JodOhjaajaCmsUtil jodOhjaajaCmsUtil;
  @Reference private VocabularyInitializer vocabularyInitializer;

  /**
   * The main categories found are taken over first. A missing one is created only if the vocabulary
   * has no other top-level category, which could be the same one with another title: creating it
   * would duplicate the category and its menu item in the public navigation.
   */
  @Override
  public synchronized List<AssetCategory> initMainCategories() throws PortalException {
    var vocabulary =
        vocabularyInitializer.getOrCreateVocabulary(
            CATEGORY_VOCABULARY_EXTERNAL_REFERENCE_CODE,
            LEGACY_VOCABULARY_EXTERNAL_REFERENCE_CODE,
            VOCABULARY_NAME,
            VOCABULARY_TITLES);

    var categories = new LinkedHashMap<MainCategory, AssetCategory>();
    for (var mainCategory : MAIN_CATEGORIES) {
      categories.put(mainCategory, fetchOrTakeOverMainCategory(vocabulary, mainCategory));
    }

    var unknownCategoryTitles =
        vocabulary.getCategories().stream()
            .filter(
                category ->
                    category.getParentCategoryId() == 0
                        && !TakeOverUtil.isManaged(category.getExternalReferenceCode()))
            .map(category -> category.getTitle(LocaleUtil.getSiteDefault()))
            .toList();

    var mainCategories = new ArrayList<AssetCategory>();
    for (var entry : categories.entrySet()) {
      var category = entry.getValue();
      if (category == null && unknownCategoryTitles.isEmpty()) {
        category = addMainCategory(vocabulary, entry.getKey());
      }
      if (category == null) {
        log.error(
            "Main category "
                + entry.getKey().externalReferenceCode()
                + " not found and not created, as the vocabulary has the categories "
                + unknownCategoryTitles
                + ": give the category one of the titles "
                + entry.getKey().titles().values());
        continue;
      }
      // The guest views the categories through the public navigation
      vocabularyInitializer.ensureGuestViewPermission(
          category.getCompanyId(), AssetCategory.class.getName(), category.getCategoryId());
      mainCategories.add(category);
    }
    return mainCategories;
  }

  @Override
  public AssetVocabulary fetchCategoryVocabulary() {
    return vocabularyInitializer.fetchVocabulary(CATEGORY_VOCABULARY_EXTERNAL_REFERENCE_CODE);
  }

  @Override
  public AssetCategory fetchMainCategory(String externalReferenceCode) {
    var vocabulary = fetchCategoryVocabulary();
    return vocabulary == null ? null : fetchCategory(vocabulary, externalReferenceCode);
  }

  private AssetCategory fetchCategory(AssetVocabulary vocabulary, String externalReferenceCode) {
    var category =
        assetCategoryLocalService.fetchAssetCategoryByExternalReferenceCode(
            externalReferenceCode, vocabulary.getGroupId());
    return category != null && category.getVocabularyId() == vocabulary.getVocabularyId()
        ? category
        : null;
  }

  private AssetCategory fetchOrTakeOverMainCategory(
      AssetVocabulary vocabulary, MainCategory mainCategory) {
    var category = fetchCategory(vocabulary, mainCategory.externalReferenceCode());
    if (category != null) {
      return category;
    }

    if (mainCategory.legacyExternalReferenceCode() != null) {
      category = fetchCategory(vocabulary, mainCategory.legacyExternalReferenceCode());
    }
    if (category == null) {
      category =
          vocabulary.getCategories().stream()
              .filter(
                  vocabularyCategory ->
                      vocabularyCategory.getParentCategoryId() == 0
                          && !TakeOverUtil.isManaged(vocabularyCategory.getExternalReferenceCode())
                          && TakeOverUtil.hasAnyTitle(
                              vocabularyCategory.getTitleMap(), mainCategory.titles().values()))
              .findFirst()
              .orElse(null);
    }
    return category == null
        ? null
        : takeOverCategory(category, mainCategory.externalReferenceCode());
  }

  private AssetCategory addMainCategory(AssetVocabulary vocabulary, MainCategory mainCategory)
      throws PortalException {
    var serviceContext = new ServiceContext();
    serviceContext.setAddGroupPermissions(true);
    serviceContext.setAddGuestPermissions(true);
    var category =
        assetCategoryLocalService.addCategory(
            mainCategory.externalReferenceCode(),
            AdminUtil.getAdminUser(vocabulary.getCompanyId()).getUserId(),
            vocabulary.getGroupId(),
            0,
            LocalizedMapUtil.getLocalizedMap(mainCategory.titles()),
            Map.of(),
            vocabulary.getVocabularyId(),
            false,
            null,
            serviceContext);
    log.info("Created main category " + mainCategory.externalReferenceCode());
    return category;
  }

  // Only the external reference code is changed, the category can be edited in the UI
  private AssetCategory takeOverCategory(AssetCategory category, String externalReferenceCode) {
    var previousExternalReferenceCode = category.getExternalReferenceCode();
    category.setExternalReferenceCode(externalReferenceCode);
    category = assetCategoryLocalService.updateAssetCategory(category);
    log.info(
        "Took over main category "
            + category.getCategoryId()
            + ", changed its external reference code from "
            + previousExternalReferenceCode
            + " to "
            + externalReferenceCode);
    updateMenuItemReferences(previousExternalReferenceCode, externalReferenceCode);
    return category;
  }

  // Menu items created in the DXP 2026 UI refer to the category by its external reference code
  private void updateMenuItemReferences(
      String previousExternalReferenceCode, String externalReferenceCode) {
    var groupId = jodOhjaajaCmsUtil.getJodOhjaajaCmsGroup().getGroupId();
    for (var menu : siteNavigationMenuLocalService.getSiteNavigationMenus(groupId)) {
      for (var menuItem :
          siteNavigationMenuItemLocalService.getSiteNavigationMenuItems(
              menu.getSiteNavigationMenuId())) {
        if (!AssetCategory.class.getName().equals(menuItem.getType())) {
          continue;
        }
        var typeSettings = UnicodePropertiesBuilder.fastLoad(menuItem.getTypeSettings()).build();
        if (previousExternalReferenceCode.equals(
            typeSettings.getProperty(PROPERTY_EXTERNAL_REFERENCE_CODE))) {
          typeSettings.setProperty(PROPERTY_EXTERNAL_REFERENCE_CODE, externalReferenceCode);
          menuItem.setTypeSettings(typeSettings.toString());
          siteNavigationMenuItemLocalService.updateSiteNavigationMenuItem(menuItem);
          log.info(
              "Updated the category reference of menu item "
                  + menuItem.getSiteNavigationMenuItemId());
        }
      }
    }
  }

  private record MainCategory(
      String externalReferenceCode,
      String legacyExternalReferenceCode,
      Map<String, String> titles) {}
}
