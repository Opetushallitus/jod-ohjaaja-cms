/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.navigation.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.asset.kernel.model.AssetCategory;
import com.liferay.asset.kernel.model.AssetVocabulary;
import com.liferay.asset.kernel.service.AssetCategoryLocalService;
import com.liferay.asset.kernel.service.AssetVocabularyLocalService;
import com.liferay.dynamic.data.mapping.service.DDMStructureLocalService;
import com.liferay.expando.kernel.model.ExpandoTableConstants;
import com.liferay.expando.kernel.service.ExpandoValueLocalService;
import com.liferay.journal.model.JournalArticle;
import com.liferay.journal.service.JournalArticleLocalService;
import com.liferay.portal.kernel.model.ResourceConstants;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.model.role.RoleConstants;
import com.liferay.portal.kernel.security.permission.ActionKeys;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.PermissionCheckerFactory;
import com.liferay.portal.kernel.security.permission.PermissionThreadLocal;
import com.liferay.portal.kernel.service.ResourcePermissionLocalService;
import com.liferay.portal.kernel.service.RoleLocalService;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.test.util.ServiceContextTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.UnicodePropertiesBuilder;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.portal.security.service.access.policy.service.SAPEntryLocalService;
import com.liferay.portal.test.log.LoggerTestUtil;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import com.liferay.site.navigation.model.SiteNavigationMenu;
import com.liferay.site.navigation.model.SiteNavigationMenuItem;
import com.liferay.site.navigation.service.SiteNavigationMenuItemLocalService;
import com.liferay.site.navigation.service.SiteNavigationMenuLocalService;
import fi.okm.jod.ohjaaja.cms.navigation.dto.NavigationDto;
import fi.okm.jod.ohjaaja.cms.navigation.dto.NavigationItemDto;
import fi.okm.jod.ohjaaja.cms.navigation.exception.NavigationMenuItemPersistenceException;
import fi.okm.jod.ohjaaja.cms.navigation.exception.StudyProgramListingMissingException;
import fi.okm.jod.ohjaaja.cms.navigation.service.MainCategoryService;
import fi.okm.jod.ohjaaja.cms.navigation.service.NavigationService;
import fi.okm.jod.ohjaaja.cms.util.JodOhjaajaCmsUtil;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Integration tests for NavigationService. Tests navigation menu management functionality. */
@RunWith(Arquillian.class)
public class NavigationServiceTest {

  @ClassRule @Rule
  public static final LiferayIntegrationTestRule liferayIntegrationTestRule =
      new LiferayIntegrationTestRule();

  @Inject private static NavigationService navigationService;
  @Inject private static MainCategoryService mainCategoryService;
  @Inject private static JodOhjaajaCmsUtil jodOhjaajaCmsUtil;
  @Inject private static PermissionCheckerFactory permissionCheckerFactory;
  @Inject private static SiteNavigationMenuLocalService siteNavigationMenuLocalService;
  @Inject private static SiteNavigationMenuItemLocalService siteNavigationMenuItemLocalService;
  @Inject private static SAPEntryLocalService sapEntryLocalService;
  @Inject private static AssetCategoryLocalService assetCategoryLocalService;
  @Inject private static AssetVocabularyLocalService assetVocabularyLocalService;
  @Inject private static RoleLocalService roleLocalService;
  @Inject private static ResourcePermissionLocalService resourcePermissionLocalService;
  @Inject private static ExpandoValueLocalService expandoValueLocalService;
  @Inject private static DDMStructureLocalService ddmStructureLocalService;
  @Inject private static JournalArticleLocalService journalArticleLocalService;

  private static PermissionChecker originalPermissionChecker;
  private static Long TEST_GROUP_ID;

  @BeforeClass
  public static void setUpClass() throws Exception {
    TEST_GROUP_ID = jodOhjaajaCmsUtil.getJodOhjaajaCmsGroup().getGroupId();
    // Set up permissions
    originalPermissionChecker = PermissionThreadLocal.getPermissionChecker();
    User adminUser = TestPropsValues.getUser();
    PermissionChecker permissionChecker = permissionCheckerFactory.create(adminUser);
    PermissionThreadLocal.setPermissionChecker(permissionChecker);

    // Initialize navigation
    navigationService.initNavigation();
  }

  @AfterClass
  public static void tearDownClass() {
    PermissionThreadLocal.setPermissionChecker(originalPermissionChecker);
  }

  @Test
  public void shouldInitializeNavigation() {
    System.out.println("\n=== Testing Navigation Initialization ===");

    try {
      navigationService.initNavigation();
      System.out.println("✅ Navigation initialized successfully");
    } catch (Exception e) {
      Assert.fail("Navigation initialization should not throw exception: " + e.getMessage());
    }
  }

  @Test
  public void shouldCreatePublicServiceAccessPolicyForNavigationApi() throws Exception {
    var sapEntry =
        sapEntryLocalService.fetchSAPEntry(
            TestPropsValues.getCompanyId(), "JOD_OHJAAJA_NAVIGATION");

    Assert.assertNotNull("SAP JOD_OHJAAJA_NAVIGATION should exist", sapEntry);
    Assert.assertTrue("SAP should be enabled", sapEntry.isEnabled());
    Assert.assertTrue("SAP should apply to guests", sapEntry.isDefaultSAPEntry());
    Assert.assertEquals(
        "fi.okm.jod.ohjaaja.cms.navigation.rest.application.NavigationRestApplication#*",
        sapEntry.getAllowedServiceSignatures());
  }

  @Test
  public void shouldRestorePermissionCheckerAfterInitNavigation() {
    var permissionChecker = PermissionThreadLocal.getPermissionChecker();

    navigationService.initNavigation();

    Assert.assertSame(permissionChecker, PermissionThreadLocal.getPermissionChecker());
  }

  @Test
  public void shouldCreateNavigationMenuOnlyOnce() {
    var navigationMenu = fetchNavigationMenu();
    Assert.assertNotNull("The navigation menu should be created on startup", navigationMenu);
    var menuCount = siteNavigationMenuLocalService.getSiteNavigationMenusCount(TEST_GROUP_ID);

    navigationService.initNavigation();

    Assert.assertEquals(
        menuCount, siteNavigationMenuLocalService.getSiteNavigationMenusCount(TEST_GROUP_ID));
    // The navigation is read with the guest's permissions, so the guest must be able to view it
    Assert.assertEquals(
        Long.valueOf(navigationMenu.getSiteNavigationMenuId()),
        navigationService.getNavigation().id());
  }

  @Test
  public void shouldTakeOverNavigationMenuWithoutExternalReferenceCode() {
    // Only an unambiguous menu can be checked to be taken over
    Assume.assumeTrue(
        siteNavigationMenuLocalService.getSiteNavigationMenusCount(TEST_GROUP_ID) == 1);
    var navigationMenu = fetchNavigationMenu();
    // Like a menu created by hand before the menu had a fixed external reference code
    navigationMenu.setExternalReferenceCode("hand-made-menu-" + System.currentTimeMillis());
    siteNavigationMenuLocalService.updateSiteNavigationMenu(navigationMenu);

    try {
      navigationService.initNavigation();

      var takenOverMenu = fetchNavigationMenu();
      Assert.assertNotNull(takenOverMenu);
      Assert.assertEquals(
          navigationMenu.getSiteNavigationMenuId(), takenOverMenu.getSiteNavigationMenuId());
      Assert.assertEquals(
          1, siteNavigationMenuLocalService.getSiteNavigationMenusCount(TEST_GROUP_ID));
    } finally {
      if (fetchNavigationMenu() == null) {
        navigationMenu.setExternalReferenceCode(
            NavigationService.NAVIGATION_MENU_EXTERNAL_REFERENCE_CODE);
        siteNavigationMenuLocalService.updateSiteNavigationMenu(navigationMenu);
      }
    }
  }

  @Test
  public void shouldCreateMainCategoriesAsTheTopLevelOfTheNavigation() {
    var expectedTitles =
        List.of("Ohjaajan tietosisällöt", "Asiakastyön tueksi", "Ammatillinen kehittyminen");
    var mainCategories = getMainCategories();
    var vocabulary = mainCategoryService.fetchCategoryVocabulary();
    Assert.assertNotNull(vocabulary);

    var topLevelCategoryIds =
        navigationService.getNavigation().navigationItems().stream()
            .filter(item -> "CategoryMain".equals(item.type()))
            .map(NavigationItemDto::categoryId)
            .toList();
    for (int i = 0; i < mainCategories.size(); i++) {
      var mainCategory = mainCategories.get(i);
      Assert.assertEquals(expectedTitles.get(i), mainCategory.getTitle("fi_FI"));
      Assert.assertEquals(vocabulary.getVocabularyId(), mainCategory.getVocabularyId());
    }
    // In this order, other menu items may be in between
    Assert.assertEquals(
        mainCategories.stream().map(AssetCategory::getCategoryId).toList(),
        topLevelCategoryIds.stream()
            .filter(
                categoryId ->
                    mainCategories.stream()
                        .anyMatch(mainCategory -> mainCategory.getCategoryId() == categoryId))
            .toList());

    // Nothing is created again
    var menuItemCount = countMenuItems();
    navigationService.initNavigation();
    Assert.assertEquals(menuItemCount, countMenuItems());
  }

  @Test
  public void shouldTakeOverMainCategoryWithoutChangingIt() throws Exception {
    var category =
        mainCategoryService.fetchMainCategory(
            MainCategoryService.PROFESSIONAL_DEVELOPMENT_CATEGORY_EXTERNAL_REFERENCE_CODE);
    var originalTitle = category.getTitle("fi_FI");
    // As created by the study program importer and edited in the UI, and its menu item as created
    // in the DXP 2026 UI
    var legacyExternalReferenceCode = "ohjaaja-study-program-parent-category";
    category.setExternalReferenceCode(legacyExternalReferenceCode);
    category.setTitle("Muokattu otsikko", LocaleUtil.fromLanguageId("fi_FI"));
    assetCategoryLocalService.updateAssetCategory(category);
    var menuItem = findMenuItemOfCategory(category.getCategoryId());
    var originalTypeSettings = menuItem.getTypeSettings();
    var typeSettings = UnicodePropertiesBuilder.fastLoad(originalTypeSettings).build();
    typeSettings.remove("classPK");
    typeSettings.setProperty("externalReferenceCode", legacyExternalReferenceCode);
    menuItem.setTypeSettings(typeSettings.toString());
    siteNavigationMenuItemLocalService.updateSiteNavigationMenuItem(menuItem);

    try {
      navigationService.initNavigation();

      var takenOver = assetCategoryLocalService.getAssetCategory(category.getCategoryId());
      Assert.assertEquals(
          MainCategoryService.PROFESSIONAL_DEVELOPMENT_CATEGORY_EXTERNAL_REFERENCE_CODE,
          takenOver.getExternalReferenceCode());
      Assert.assertEquals("Muokattu otsikko", takenOver.getTitle("fi_FI"));
      // The menu item still refers to the category, no other one was created
      var item =
          findItem(
              navigationService.getNavigation().navigationItems(),
              menuItem.getSiteNavigationMenuItemId());
      Assert.assertEquals(Long.valueOf(category.getCategoryId()), item.categoryId());
      Assert.assertEquals(1, countMenuItemsOfCategory(category.getCategoryId()));
    } finally {
      var restored = assetCategoryLocalService.getAssetCategory(category.getCategoryId());
      restored.setTitle(originalTitle, LocaleUtil.fromLanguageId("fi_FI"));
      assetCategoryLocalService.updateAssetCategory(restored);
      var restoredMenuItem =
          siteNavigationMenuItemLocalService.getSiteNavigationMenuItem(
              menuItem.getSiteNavigationMenuItemId());
      restoredMenuItem.setTypeSettings(originalTypeSettings);
      siteNavigationMenuItemLocalService.updateSiteNavigationMenuItem(restoredMenuItem);
    }
  }

  @Test
  public void shouldGrantGuestViewPermissionOnMainCategories() throws Exception {
    var category =
        mainCategoryService.fetchMainCategory(
            MainCategoryService.INFORMATION_CONTENT_CATEGORY_EXTERNAL_REFERENCE_CODE);
    var guestRole = roleLocalService.getRole(category.getCompanyId(), RoleConstants.GUEST);
    // As a category created by hand without the permission
    resourcePermissionLocalService.setResourcePermissions(
        category.getCompanyId(),
        AssetCategory.class.getName(),
        ResourceConstants.SCOPE_INDIVIDUAL,
        String.valueOf(category.getCategoryId()),
        guestRole.getRoleId(),
        new String[0]);

    navigationService.initNavigation();

    var resourcePermission =
        resourcePermissionLocalService.fetchResourcePermission(
            category.getCompanyId(),
            AssetCategory.class.getName(),
            ResourceConstants.SCOPE_INDIVIDUAL,
            String.valueOf(category.getCategoryId()),
            guestRole.getRoleId());
    Assert.assertTrue(resourcePermission.hasActionId(ActionKeys.VIEW));
  }

  @Test
  public void shouldNotCreateMainCategoryIfTheVocabularyHasUnknownCategories() throws Exception {
    var category =
        mainCategoryService.fetchMainCategory(
            MainCategoryService.CUSTOMER_WORK_CATEGORY_EXTERNAL_REFERENCE_CODE);
    var originalTitleMap = category.getTitleMap();
    // As a main category renamed in the UI before it was managed in code
    category.setExternalReferenceCode("hand-made-" + System.currentTimeMillis());
    category.setTitleMap(Map.of(LocaleUtil.fromLanguageId("fi_FI"), "Uudelleen nimetty"));
    assetCategoryLocalService.updateAssetCategory(category);
    var categoryCount = mainCategoryService.fetchCategoryVocabulary().getCategoriesCount();

    try (var logCapture =
        LoggerTestUtil.configureLog4JLogger(
            "fi.okm.jod.ohjaaja.cms.navigation.service.MainCategoryServiceImpl",
            LoggerTestUtil.ERROR)) {
      navigationService.initNavigation();

      Assert.assertEquals(1, logCapture.getLogEntries().size());
    }
    Assert.assertEquals(
        categoryCount, mainCategoryService.fetchCategoryVocabulary().getCategoriesCount());
    Assert.assertNull(
        mainCategoryService.fetchMainCategory(
            MainCategoryService.CUSTOMER_WORK_CATEGORY_EXTERNAL_REFERENCE_CODE));

    // Taken over again once it has its title back
    var renamed = assetCategoryLocalService.getAssetCategory(category.getCategoryId());
    renamed.setTitleMap(originalTitleMap);
    assetCategoryLocalService.updateAssetCategory(renamed);
    navigationService.initNavigation();
    Assert.assertEquals(
        MainCategoryService.CUSTOMER_WORK_CATEGORY_EXTERNAL_REFERENCE_CODE,
        assetCategoryLocalService
            .getAssetCategory(category.getCategoryId())
            .getExternalReferenceCode());
  }

  @Test
  public void shouldInitializeMissingNavigationMenuWhenTheNavigationIsRead() throws Exception {
    // Only an unambiguous menu can be checked to be taken over
    Assume.assumeTrue(
        siteNavigationMenuLocalService.getSiteNavigationMenusCount(TEST_GROUP_ID) == 1);
    var navigationMenu = fetchNavigationMenu();
    // As if taking it over had failed on startup
    navigationMenu.setExternalReferenceCode("not-taken-over-" + System.currentTimeMillis());
    siteNavigationMenuLocalService.updateSiteNavigationMenu(navigationMenu);
    navigationService.clearNavigationCache();

    try {
      var navigation = navigationService.getNavigation();

      Assert.assertNotNull(navigation);
      Assert.assertEquals(Long.valueOf(navigationMenu.getSiteNavigationMenuId()), navigation.id());
      Assert.assertNotNull(fetchNavigationMenu());
    } finally {
      if (fetchNavigationMenu() == null) {
        var menu =
            siteNavigationMenuLocalService.getSiteNavigationMenu(
                navigationMenu.getSiteNavigationMenuId());
        menu.setExternalReferenceCode(NavigationService.NAVIGATION_MENU_EXTERNAL_REFERENCE_CODE);
        siteNavigationMenuLocalService.updateSiteNavigationMenu(menu);
      }
    }
  }

  @Test
  public void shouldTakeOverMainCategoryByTitle() throws Exception {
    var category =
        mainCategoryService.fetchMainCategory(
            MainCategoryService.CUSTOMER_WORK_CATEGORY_EXTERNAL_REFERENCE_CODE);
    // As created by hand
    category.setExternalReferenceCode("hand-made-" + System.currentTimeMillis());
    assetCategoryLocalService.updateAssetCategory(category);

    navigationService.initNavigation();

    Assert.assertEquals(
        MainCategoryService.CUSTOMER_WORK_CATEGORY_EXTERNAL_REFERENCE_CODE,
        assetCategoryLocalService
            .getAssetCategory(category.getCategoryId())
            .getExternalReferenceCode());
    Assert.assertEquals(1, countMenuItemsOfCategory(category.getCategoryId()));
  }

  private static List<AssetCategory> getMainCategories() {
    return Stream.of(
            MainCategoryService.INFORMATION_CONTENT_CATEGORY_EXTERNAL_REFERENCE_CODE,
            MainCategoryService.CUSTOMER_WORK_CATEGORY_EXTERNAL_REFERENCE_CODE,
            MainCategoryService.PROFESSIONAL_DEVELOPMENT_CATEGORY_EXTERNAL_REFERENCE_CODE)
        .map(
            externalReferenceCode -> {
              var category = mainCategoryService.fetchMainCategory(externalReferenceCode);
              Assert.assertNotNull(externalReferenceCode + " should exist", category);
              return category;
            })
        .toList();
  }

  private static List<SiteNavigationMenuItem> getMenuItems() {
    return siteNavigationMenuItemLocalService.getSiteNavigationMenuItems(
        fetchNavigationMenu().getSiteNavigationMenuId());
  }

  private static int countMenuItems() {
    return getMenuItems().size();
  }

  private static SiteNavigationMenuItem findMenuItemOfCategory(long categoryId) {
    return getMenuItems().stream()
        .filter(menuItem -> isMenuItemOfCategory(menuItem, categoryId))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No menu item for category " + categoryId));
  }

  private static long countMenuItemsOfCategory(long categoryId) {
    return getMenuItems().stream()
        .filter(menuItem -> isMenuItemOfCategory(menuItem, categoryId))
        .count();
  }

  private static boolean isMenuItemOfCategory(SiteNavigationMenuItem menuItem, long categoryId) {
    if (!AssetCategory.class.getName().equals(menuItem.getType())) {
      return false;
    }
    var typeSettings = UnicodePropertiesBuilder.fastLoad(menuItem.getTypeSettings()).build();
    var category = assetCategoryLocalService.fetchAssetCategory(categoryId);
    return String.valueOf(categoryId).equals(typeSettings.getProperty("classPK"))
        || category
            .getExternalReferenceCode()
            .equals(typeSettings.getProperty("externalReferenceCode"));
  }

  @Test
  public void shouldCreateStudyProgramsListingMenuItemAndMainCategoryMenuItem() throws Exception {
    assumeNoStudyProgramsListingMenuItem();
    var vocabulary = addVocabulary();
    SiteNavigationMenuItem listingMenuItem = null;
    SiteNavigationMenuItem mainMenuItem = null;

    try {
      var mainCategory = addCategory(vocabulary.getVocabularyId(), "Main");
      var studyProgramsCategory = addCategory(vocabulary.getVocabularyId(), "Study programs");

      listingMenuItem =
          navigationService.getOrCreateStudyProgramsParentMenuItem(
              mainCategory, studyProgramsCategory);
      mainMenuItem =
          siteNavigationMenuItemLocalService.getSiteNavigationMenuItem(
              listingMenuItem.getParentSiteNavigationMenuItemId());

      Assert.assertEquals(
          NavigationService.STUDY_PROGRAMS_LISTING_MENU_ITEM_EXTERNAL_REFERENCE_CODE,
          listingMenuItem.getExternalReferenceCode());
      Assert.assertEquals(
          mainCategory.getExternalReferenceCode(), mainMenuItem.getExternalReferenceCode());
      Assert.assertEquals(0, mainMenuItem.getParentSiteNavigationMenuItemId());
      Assert.assertEquals(
          fetchNavigationMenu().getSiteNavigationMenuId(), mainMenuItem.getSiteNavigationMenuId());

      // Nothing is created again
      Assert.assertEquals(
          listingMenuItem.getSiteNavigationMenuItemId(),
          navigationService
              .getOrCreateStudyProgramsParentMenuItem(mainCategory, studyProgramsCategory)
              .getSiteNavigationMenuItemId());
      Assert.assertEquals(
          listingMenuItem.getSiteNavigationMenuItemId(),
          navigationService.getStudyProgramsParentMenuItem().getSiteNavigationMenuItemId());

      // Found by its external reference code if its type was never set, e.g. if setting it failed
      expandoValueLocalService.deleteValue(
          listingMenuItem.getCompanyId(),
          SiteNavigationMenuItem.class.getName(),
          ExpandoTableConstants.DEFAULT_TABLE_NAME,
          "Type",
          listingMenuItem.getSiteNavigationMenuItemId());
      Assert.assertEquals(
          listingMenuItem.getSiteNavigationMenuItemId(),
          navigationService
              .getOrCreateStudyProgramsParentMenuItem(mainCategory, studyProgramsCategory)
              .getSiteNavigationMenuItemId());
      Assert.assertEquals(
          listingMenuItem.getSiteNavigationMenuItemId(),
          navigationService.getStudyProgramsParentMenuItem().getSiteNavigationMenuItemId());

      // Not used if its type was changed in the UI, and no other one can be created
      listingMenuItem.getExpandoBridge().setAttribute("Type", new String[] {"Article"}, false);
      Assert.assertThrows(
          NavigationMenuItemPersistenceException.class,
          () ->
              navigationService.getOrCreateStudyProgramsParentMenuItem(
                  mainCategory, studyProgramsCategory));
      listingMenuItem
          .getExpandoBridge()
          .setAttribute("Type", new String[] {"StudyProgramsListing"}, false);

      assertNavigationItems(mainMenuItem, mainCategory, listingMenuItem, studyProgramsCategory);
    } finally {
      deleteMenuItems(listingMenuItem, mainMenuItem);
      assetVocabularyLocalService.deleteVocabulary(vocabulary);
    }
  }

  @Test
  public void shouldCreateStudyProgramsListingMenuItemUnderExistingMainCategoryMenuItem()
      throws Exception {
    assumeNoStudyProgramsListingMenuItem();
    var vocabulary = addVocabulary();
    SiteNavigationMenuItem listingMenuItem = null;
    SiteNavigationMenuItem mainMenuItem = null;

    try {
      var mainCategory = addCategory(vocabulary.getVocabularyId(), "Main");
      var studyProgramsCategory = addCategory(vocabulary.getVocabularyId(), "Study programs");
      var categoryClassName = AssetCategory.class.getName();
      // Format used by menu items created in the DXP 2026 UI: no classPK, only the category ERC
      mainMenuItem =
          siteNavigationMenuItemLocalService.addSiteNavigationMenuItem(
              "nav-test-main-item-" + System.currentTimeMillis(),
              TestPropsValues.getUserId(),
              TEST_GROUP_ID,
              fetchNavigationMenu().getSiteNavigationMenuId(),
              0,
              categoryClassName,
              "className="
                  + categoryClassName
                  + "\nexternalReferenceCode="
                  + mainCategory.getExternalReferenceCode()
                  + "\ntitle=Main\n",
              getServiceContext());
      mainMenuItem.getExpandoBridge().setAttribute("Type", new String[] {"CategoryMain"}, false);

      listingMenuItem =
          navigationService.getOrCreateStudyProgramsParentMenuItem(
              mainCategory, studyProgramsCategory);

      Assert.assertEquals(
          mainMenuItem.getSiteNavigationMenuItemId(),
          listingMenuItem.getParentSiteNavigationMenuItemId());
      // Taken over: found by the external reference code of its category from now on
      Assert.assertEquals(
          mainCategory.getExternalReferenceCode(),
          siteNavigationMenuItemLocalService
              .getSiteNavigationMenuItem(mainMenuItem.getSiteNavigationMenuItemId())
              .getExternalReferenceCode());
      assertNavigationItems(mainMenuItem, mainCategory, listingMenuItem, studyProgramsCategory);
    } finally {
      deleteMenuItems(listingMenuItem, mainMenuItem);
      assetVocabularyLocalService.deleteVocabulary(vocabulary);
    }
  }

  @Test
  public void shouldTakeOverStudyProgramsListingMenuItemByItsType() throws Exception {
    assumeNoStudyProgramsListingMenuItem();
    var vocabulary = addVocabulary();
    SiteNavigationMenuItem handMadeMenuItem = null;

    try {
      var mainCategory = addCategory(vocabulary.getVocabularyId(), "Main");
      var studyProgramsCategory = addCategory(vocabulary.getVocabularyId(), "Study programs");
      // As created by hand, recognized only by its type
      handMadeMenuItem =
          siteNavigationMenuItemLocalService.addSiteNavigationMenuItem(
              "nav-test-listing-item-" + System.currentTimeMillis(),
              TestPropsValues.getUserId(),
              TEST_GROUP_ID,
              fetchNavigationMenu().getSiteNavigationMenuId(),
              0,
              "url",
              "name=Koulutukset\nurl=/koulutukset\n",
              getServiceContext());
      handMadeMenuItem
          .getExpandoBridge()
          .setAttribute("Type", new String[] {"StudyProgramsListing"}, false);

      var listingMenuItem =
          navigationService.getOrCreateStudyProgramsParentMenuItem(
              mainCategory, studyProgramsCategory);

      Assert.assertEquals(
          handMadeMenuItem.getSiteNavigationMenuItemId(),
          listingMenuItem.getSiteNavigationMenuItemId());
      Assert.assertEquals(
          NavigationService.STUDY_PROGRAMS_LISTING_MENU_ITEM_EXTERNAL_REFERENCE_CODE,
          listingMenuItem.getExternalReferenceCode());
      Assert.assertEquals(
          handMadeMenuItem.getSiteNavigationMenuItemId(),
          navigationService.getStudyProgramsParentMenuItem().getSiteNavigationMenuItemId());
    } finally {
      deleteMenuItems(handMadeMenuItem);
      assetVocabularyLocalService.deleteVocabulary(vocabulary);
    }
  }

  private static void assertNavigationItems(
      SiteNavigationMenuItem mainMenuItem,
      AssetCategory mainCategory,
      SiteNavigationMenuItem listingMenuItem,
      AssetCategory studyProgramsCategory) {
    var main =
        findItem(
            navigationService.getNavigation().navigationItems(),
            mainMenuItem.getSiteNavigationMenuItemId());
    Assert.assertEquals("CategoryMain", main.type());
    Assert.assertEquals(Long.valueOf(mainCategory.getCategoryId()), main.categoryId());

    var listing = findItem(main.children(), listingMenuItem.getSiteNavigationMenuItemId());
    Assert.assertEquals("StudyProgramsListing", listing.type());
    Assert.assertEquals(Long.valueOf(studyProgramsCategory.getCategoryId()), listing.categoryId());
    Assert.assertEquals(
        studyProgramsCategory.getExternalReferenceCode(), listing.externalReferenceCode());
  }

  // The importer test of another module may have created the menu item in the same container
  private static void assumeNoStudyProgramsListingMenuItem() {
    try {
      navigationService.getStudyProgramsParentMenuItem();
      Assume.assumeTrue("A StudyProgramsListing menu item already exists", false);
    } catch (StudyProgramListingMissingException e) {
      // Expected
    } catch (Exception e) {
      Assert.fail("Unexpected exception: " + e.getMessage());
    }
  }

  private static AssetVocabulary addVocabulary() throws Exception {
    return assetVocabularyLocalService.addVocabulary(
        TestPropsValues.getUserId(),
        TEST_GROUP_ID,
        "nav-test-vocabulary-" + System.nanoTime(),
        getServiceContext());
  }

  private static AssetCategory addCategory(long vocabularyId, String title) throws Exception {
    return assetCategoryLocalService.addCategory(
        TestPropsValues.getUserId(),
        TEST_GROUP_ID,
        title + " " + System.nanoTime(),
        vocabularyId,
        getServiceContext());
  }

  // The navigation is read with the guest's permissions
  private static ServiceContext getServiceContext() throws Exception {
    var serviceContext =
        ServiceContextTestUtil.getServiceContext(TEST_GROUP_ID, TestPropsValues.getUserId());
    serviceContext.setAddGroupPermissions(true);
    serviceContext.setAddGuestPermissions(true);
    return serviceContext;
  }

  private static void deleteMenuItems(SiteNavigationMenuItem... menuItems) throws Exception {
    for (var menuItem : menuItems) {
      if (menuItem != null
          && siteNavigationMenuItemLocalService.fetchSiteNavigationMenuItem(
                  menuItem.getSiteNavigationMenuItemId())
              != null) {
        siteNavigationMenuItemLocalService.deleteSiteNavigationMenuItem(
            menuItem.getSiteNavigationMenuItemId());
      }
    }
  }

  private static SiteNavigationMenu fetchNavigationMenu() {
    return siteNavigationMenuLocalService.fetchSiteNavigationMenuByExternalReferenceCode(
        NavigationService.NAVIGATION_MENU_EXTERNAL_REFERENCE_CODE, TEST_GROUP_ID);
  }

  @Test
  public void shouldResolveArticleMenuItemsByExternalReferenceCodeAndFallBackForBrokenOnes()
      throws Exception {
    var userId = TestPropsValues.getUserId();
    var serviceContext = ServiceContextTestUtil.getServiceContext(TEST_GROUP_ID, userId);
    var suffix = String.valueOf(System.currentTimeMillis());
    var articleClassName = JournalArticle.class.getName();

    var structure =
        ddmStructureLocalService.addStructure(
            null,
            userId,
            TEST_GROUP_ID,
            0,
            PortalUtil.getClassNameId(articleClassName),
            "NAV_TEST_STRUCTURE_" + suffix,
            Map.of(Locale.US, "Navigation test structure"),
            null,
            "<?xml version=\"1.0\"?>"
                + "<root available-locales=\"en_US\" default-locale=\"en_US\">"
                + "<dynamic-element dataType=\"string\" name=\"content\" type=\"text\">"
                + "<meta-data locale=\"en_US\"><entry name=\"label\"><![CDATA[Content]]></entry>"
                + "</meta-data></dynamic-element></root>",
            "xml",
            serviceContext);
    var article =
        journalArticleLocalService.addArticle(
            "nav-test-article-" + suffix,
            userId,
            TEST_GROUP_ID,
            0,
            Map.of(Locale.US, "Navigation test article"),
            null,
            "<?xml version=\"1.0\"?>"
                + "<root available-locales=\"en_US\" default-locale=\"en_US\">"
                + "<dynamic-element name=\"content\" type=\"text\">"
                + "<dynamic-content language-id=\"en_US\"><![CDATA[x]]></dynamic-content>"
                + "</dynamic-element></root>",
            structure.getStructureId(),
            null,
            serviceContext);

    var menuId = navigationService.getNavigation().id();
    // Format used by menu items created in the DXP 2026 UI: no classPK, only the article ERC
    var ercItem =
        siteNavigationMenuItemLocalService.addSiteNavigationMenuItem(
            "nav-test-erc-item-" + suffix,
            userId,
            TEST_GROUP_ID,
            menuId,
            0,
            articleClassName,
            "className="
                + articleClassName
                + "\nexternalReferenceCode="
                + article.getExternalReferenceCode()
                + "\ntitle=Fallback title\n",
            serviceContext);
    var brokenItem =
        siteNavigationMenuItemLocalService.addSiteNavigationMenuItem(
            "nav-test-broken-item-" + suffix,
            userId,
            TEST_GROUP_ID,
            menuId,
            0,
            articleClassName,
            "title=Broken item\n",
            serviceContext);

    try {
      var items = navigationService.getNavigation().navigationItems();
      var resolved = findItem(items, ercItem.getSiteNavigationMenuItemId());
      var broken = findItem(items, brokenItem.getSiteNavigationMenuItemId());

      Assert.assertEquals(Long.valueOf(article.getResourcePrimKey()), resolved.articleId());
      Assert.assertEquals(article.getExternalReferenceCode(), resolved.externalReferenceCode());
      Assert.assertEquals("Navigation test article", resolved.name());
      Assert.assertEquals("navigation-test-article", resolved.slug());
      Assert.assertEquals("navigation-test-article", resolved.slug_i18n().get("en-US"));
      Assert.assertNull(broken.articleId());
      Assert.assertEquals("Broken item", broken.name());
      Assert.assertEquals("broken-item", broken.slug());
      Assert.assertTrue(broken.slug_i18n().isEmpty());

      // A newer draft must not replace the published version in the menu
      var draftServiceContext = ServiceContextTestUtil.getServiceContext(TEST_GROUP_ID, userId);
      draftServiceContext.setWorkflowAction(WorkflowConstants.ACTION_SAVE_DRAFT);
      journalArticleLocalService.updateArticle(
          userId,
          TEST_GROUP_ID,
          article.getFolderId(),
          article.getArticleId(),
          article.getVersion(),
          Map.of(Locale.US, "Draft title"),
          article.getDescriptionMap(),
          article.getContent(),
          article.getLayoutUuid(),
          draftServiceContext);

      var afterDraft =
          findItem(
              navigationService.getNavigation().navigationItems(),
              ercItem.getSiteNavigationMenuItemId());
      Assert.assertEquals(Long.valueOf(article.getResourcePrimKey()), afterDraft.articleId());
      Assert.assertEquals("Navigation test article", afterDraft.name());

      // The navigation is built with the guest's permissions, even when an admin requests it
      var guestRole = roleLocalService.getRole(article.getCompanyId(), "Guest");
      resourcePermissionLocalService.setResourcePermissions(
          article.getCompanyId(),
          articleClassName,
          ResourceConstants.SCOPE_INDIVIDUAL,
          String.valueOf(article.getResourcePrimKey()),
          guestRole.getRoleId(),
          new String[0]);

      var afterGuestPermissionRemoved =
          findItem(
              navigationService.getNavigation().navigationItems(),
              ercItem.getSiteNavigationMenuItemId());
      Assert.assertNull(afterGuestPermissionRemoved.articleId());
    } finally {
      siteNavigationMenuItemLocalService.deleteSiteNavigationMenuItem(ercItem);
      siteNavigationMenuItemLocalService.deleteSiteNavigationMenuItem(brokenItem);
      // Deletes all versions, including the draft
      journalArticleLocalService.deleteArticle(
          TEST_GROUP_ID, article.getArticleId(), serviceContext);
      ddmStructureLocalService.deleteStructure(structure);
    }
  }

  @Test
  public void shouldCacheNavigationUntilTheMenuChanges() throws Exception {
    var userId = TestPropsValues.getUserId();
    var serviceContext = ServiceContextTestUtil.getServiceContext(TEST_GROUP_ID, userId);
    SiteNavigationMenuItem menuItem = null;

    try {
      var navigation = getCachedNavigation();
      // The cached navigation is shared by all requests
      Assert.assertThrows(
          UnsupportedOperationException.class, () -> navigation.navigationItems().clear());

      menuItem =
          siteNavigationMenuItemLocalService.addSiteNavigationMenuItem(
              "nav-test-cache-item-" + System.currentTimeMillis(),
              userId,
              TEST_GROUP_ID,
              navigation.id(),
              0,
              "url",
              "title=Cache test item\n",
              serviceContext);

      var menuItemId = menuItem.getSiteNavigationMenuItemId();
      var navigationAfterChange = navigationService.getNavigation();
      Assert.assertNotSame(navigation, navigationAfterChange);
      Assert.assertTrue(
          navigationAfterChange.navigationItems().stream()
              .anyMatch(item -> item.id() == menuItemId));
    } finally {
      if (menuItem != null) {
        siteNavigationMenuItemLocalService.deleteSiteNavigationMenuItem(menuItem);
      }
    }
  }

  @Test
  public void shouldRestorePermissionCheckerAfterGetNavigation() {
    var permissionChecker = PermissionThreadLocal.getPermissionChecker();

    navigationService.clearNavigationCache();
    navigationService.getNavigation();

    Assert.assertSame(permissionChecker, PermissionThreadLocal.getPermissionChecker());
  }

  /**
   * Returns the navigation after checking that it is cached. Another test may change the content
   * and clear the cache between the calls, so it is tried a few times.
   */
  private static NavigationDto getCachedNavigation() {
    for (var attempt = 1; ; attempt++) {
      var navigation = navigationService.getNavigation();
      Assert.assertNotNull(navigation);
      if (navigation == navigationService.getNavigation()) {
        return navigation;
      }
      if (attempt == 3) {
        Assert.fail("The navigation should be cached");
      }
    }
  }

  private static NavigationItemDto findItem(List<NavigationItemDto> items, long id) {
    return items.stream()
        .filter(item -> item.id() == id)
        .findFirst()
        .orElseThrow(() -> new AssertionError("Navigation item " + id + " not found"));
  }

  @Test
  public void shouldGetNavigationWithItems() {
    var navigation = navigationService.getNavigation();

    Assert.assertNotNull("Navigation should not be null", navigation);
    Assert.assertNotNull("Navigation items should not be null", navigation.navigationItems());
  }
}
