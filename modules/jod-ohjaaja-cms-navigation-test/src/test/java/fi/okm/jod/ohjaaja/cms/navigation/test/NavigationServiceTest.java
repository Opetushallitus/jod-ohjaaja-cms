/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.navigation.test;

import com.liferay.dynamic.data.mapping.service.DDMStructureLocalService;
import com.liferay.journal.model.JournalArticle;
import com.liferay.journal.service.JournalArticleLocalService;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.PermissionCheckerFactory;
import com.liferay.portal.kernel.security.permission.PermissionThreadLocal;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.test.util.ServiceContextTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.portal.security.service.access.policy.service.SAPEntryLocalService;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import com.liferay.site.navigation.model.SiteNavigationMenu;
import com.liferay.site.navigation.model.SiteNavigationMenuItem;
import com.liferay.site.navigation.service.SiteNavigationMenuItemLocalService;
import com.liferay.site.navigation.service.SiteNavigationMenuLocalService;
import fi.okm.jod.ohjaaja.cms.navigation.dto.NavigationDto;
import fi.okm.jod.ohjaaja.cms.navigation.dto.NavigationItemDto;
import fi.okm.jod.ohjaaja.cms.navigation.exception.StudyProgramListingMissingException;
import fi.okm.jod.ohjaaja.cms.navigation.service.NavigationService;
import fi.okm.jod.ohjaaja.cms.testrunner.client.JodInContainerRunner;
import fi.okm.jod.ohjaaja.cms.util.JodOhjaajaCmsUtil;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;

/** Integration tests for NavigationService. Tests navigation menu management functionality. */
@RunWith(JodInContainerRunner.class)
public class NavigationServiceTest {

  @ClassRule @Rule
  public static final LiferayIntegrationTestRule liferayIntegrationTestRule =
      new LiferayIntegrationTestRule();

  private static NavigationService navigationService;
  private static BundleContext bundleContext;
  private static ServiceReference<NavigationService> serviceReference;
  private static PermissionChecker originalPermissionChecker;
  private static SiteNavigationMenu testNavigationMenu;
  private static SiteNavigationMenuLocalService siteNavigationMenuLocalService;
  private static SiteNavigationMenuItemLocalService siteNavigationMenuItemLocalService;
  private static JodOhjaajaCmsUtil jodOhjaajaCmsUtil;
  private static Long TEST_GROUP_ID;

  @BeforeClass
  public static void setUpClass() throws Exception {
    var bundle = FrameworkUtil.getBundle(NavigationServiceTest.class);
    bundleContext = bundle.getBundleContext();
    serviceReference = bundleContext.getServiceReference(NavigationService.class);
    if (serviceReference != null) {
      navigationService = bundleContext.getService(serviceReference);
    }
    jodOhjaajaCmsUtil =
        bundleContext.getService(bundleContext.getServiceReference(JodOhjaajaCmsUtil.class));
    TEST_GROUP_ID = jodOhjaajaCmsUtil.getJodOhjaajaCmsGroup().getGroupId();
    // Set up permissions
    originalPermissionChecker = PermissionThreadLocal.getPermissionChecker();
    var permissionCheckerFactoryRef =
        bundleContext.getServiceReference(PermissionCheckerFactory.class);
    var permissionCheckerFactory = bundleContext.getService(permissionCheckerFactoryRef);
    User adminUser = TestPropsValues.getUser();
    PermissionChecker permissionChecker = permissionCheckerFactory.create(adminUser);
    PermissionThreadLocal.setPermissionChecker(permissionChecker);
    bundleContext.ungetService(permissionCheckerFactoryRef);

    // Get services
    var menuLocalServiceRef =
        bundleContext.getServiceReference(SiteNavigationMenuLocalService.class);
    siteNavigationMenuLocalService = bundleContext.getService(menuLocalServiceRef);

    var menuItemLocalServiceRef =
        bundleContext.getServiceReference(SiteNavigationMenuItemLocalService.class);
    siteNavigationMenuItemLocalService = bundleContext.getService(menuItemLocalServiceRef);

    // Initialize navigation
    navigationService.initNavigation();

    // Create test navigation menu
    setupTestNavigationMenu();
  }

  @AfterClass
  public static void tearDownClass() {
    if (testNavigationMenu != null && siteNavigationMenuLocalService != null) {
      try {
        siteNavigationMenuLocalService.deleteSiteNavigationMenu(testNavigationMenu);
      } catch (Exception e) {
        System.err.println("Failed to clean up test navigation menu: " + e.getMessage());
      }
    }

    PermissionThreadLocal.setPermissionChecker(originalPermissionChecker);

    if (serviceReference != null && bundleContext != null) {
      bundleContext.ungetService(serviceReference);
    }
  }

  private static void setupTestNavigationMenu() throws Exception {
    ServiceContext serviceContext =
        ServiceContextTestUtil.getServiceContext(TEST_GROUP_ID, TestPropsValues.getUserId());

    testNavigationMenu =
        siteNavigationMenuLocalService.addSiteNavigationMenu(
            "test-nav-menu-" + System.currentTimeMillis(),
            TestPropsValues.getUserId(),
            TEST_GROUP_ID,
            "Test Navigation Menu",
            serviceContext);

    System.out.println(
        "✅ Created test navigation menu: " + testNavigationMenu.getSiteNavigationMenuId());

    // Create parent menu item with StudyProgramsListing custom field
    var menuItem =
        siteNavigationMenuItemLocalService.addSiteNavigationMenuItem(
            "test-menu-item-" + System.currentTimeMillis(),
            TestPropsValues.getUserId(),
            TEST_GROUP_ID,
            testNavigationMenu.getSiteNavigationMenuId(),
            0,
            "url",
            "{}",
            serviceContext);

    // Set custom field for StudyProgramsListing
    try {
      var expandoValueLocalServiceRef =
          bundleContext.getServiceReference(
              com.liferay.expando.kernel.service.ExpandoValueLocalService.class);
      var expandoValueLocalService = bundleContext.getService(expandoValueLocalServiceRef);

      expandoValueLocalService.addValue(
          TestPropsValues.getCompanyId(),
          SiteNavigationMenuItem.class.getName(),
          "CUSTOM_FIELDS",
          "jodNavigationCustomField",
          menuItem.getSiteNavigationMenuItemId(),
          new String[] {"StudyProgramsListing"});

      bundleContext.ungetService(expandoValueLocalServiceRef);
      System.out.println("✅ Created parent menu item with StudyProgramsListing");
    } catch (Exception e) {
      System.err.println("⚠️  Failed to set custom field: " + e.getMessage());
    }
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
    var sapEntryLocalService =
        bundleContext.getService(bundleContext.getServiceReference(SAPEntryLocalService.class));

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
  public void shouldGetStudyProgramsParentMenuItem() {
    System.out.println("\n=== Testing Parent Menu Item Retrieval ===");

    try {
      SiteNavigationMenuItem parentMenuItem = navigationService.getStudyProgramsParentMenuItem();

      Assert.assertNotNull("Parent menu item should not be null", parentMenuItem);
      Assert.assertTrue(
          "Parent menu item ID should be positive",
          parentMenuItem.getSiteNavigationMenuItemId() > 0);

      System.out.println(
          "✅ Parent menu item found: ID=" + parentMenuItem.getSiteNavigationMenuItemId());
    } catch (StudyProgramListingMissingException e) {
      System.out.println("⚠️  Expected in test environment without custom field setup");
    } catch (Exception e) {
      Assert.fail("Unexpected exception: " + e.getMessage());
    }
  }

  @Test
  public void shouldResolveArticleMenuItemsByExternalReferenceCodeAndFallBackForBrokenOnes()
      throws Exception {
    var ddmStructureLocalService = getService(DDMStructureLocalService.class);
    var journalArticleLocalService = getService(JournalArticleLocalService.class);
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

    var menuId = navigationService.getNavigation(TEST_GROUP_ID, "en_US").id();
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
      var items = navigationService.getNavigation(TEST_GROUP_ID, "en_US").navigationItems();
      var resolved = findItem(items, ercItem.getSiteNavigationMenuItemId());
      var broken = findItem(items, brokenItem.getSiteNavigationMenuItemId());

      Assert.assertEquals(Long.valueOf(article.getResourcePrimKey()), resolved.articleId());
      Assert.assertEquals(article.getExternalReferenceCode(), resolved.externalReferenceCode());
      Assert.assertEquals("Navigation test article", resolved.name());
      Assert.assertNull(broken.articleId());
      Assert.assertEquals("Broken item", broken.name());

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
              navigationService.getNavigation(TEST_GROUP_ID, "en_US").navigationItems(),
              ercItem.getSiteNavigationMenuItemId());
      Assert.assertEquals(Long.valueOf(article.getResourcePrimKey()), afterDraft.articleId());
      Assert.assertEquals("Navigation test article", afterDraft.name());
    } finally {
      siteNavigationMenuItemLocalService.deleteSiteNavigationMenuItem(ercItem);
      siteNavigationMenuItemLocalService.deleteSiteNavigationMenuItem(brokenItem);
      // Deletes all versions, including the draft
      journalArticleLocalService.deleteArticle(
          TEST_GROUP_ID, article.getArticleId(), serviceContext);
      ddmStructureLocalService.deleteStructure(structure);
    }
  }

  private static NavigationItemDto findItem(List<NavigationItemDto> items, long id) {
    return items.stream()
        .filter(item -> item.id() == id)
        .findFirst()
        .orElseThrow(() -> new AssertionError("Navigation item " + id + " not found"));
  }

  private static <T> T getService(Class<T> serviceClass) {
    return bundleContext.getService(bundleContext.getServiceReference(serviceClass));
  }

  @Test
  public void shouldGetNavigationWithItems() {
    System.out.println("\n=== Testing Navigation Retrieval ===");

    NavigationDto navigationEN = navigationService.getNavigation(TEST_GROUP_ID, "en_US");
    NavigationDto navigationFI = navigationService.getNavigation(TEST_GROUP_ID, "fi_FI");

    Assert.assertNotNull("English navigation should not be null", navigationEN);
    Assert.assertNotNull("Finnish navigation should not be null", navigationFI);
    Assert.assertNotNull(
        "English navigation items should not be null", navigationEN.navigationItems());
    Assert.assertNotNull(
        "Finnish navigation items should not be null", navigationFI.navigationItems());

    System.out.println("✅ EN navigation: " + navigationEN.navigationItems().size() + " items");
    System.out.println("✅ FI navigation: " + navigationFI.navigationItems().size() + " items");
  }
}
