/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.sitemap.test;

import com.liferay.asset.kernel.model.AssetCategory;
import com.liferay.asset.kernel.service.AssetCategoryLocalService;
import com.liferay.asset.kernel.service.AssetVocabularyLocalService;
import com.liferay.dynamic.data.mapping.form.field.type.constants.DDMFormFieldTypeConstants;
import com.liferay.dynamic.data.mapping.model.DDMForm;
import com.liferay.dynamic.data.mapping.model.DDMFormField;
import com.liferay.dynamic.data.mapping.model.DDMFormLayout;
import com.liferay.dynamic.data.mapping.model.DDMFormLayoutColumn;
import com.liferay.dynamic.data.mapping.model.DDMFormLayoutPage;
import com.liferay.dynamic.data.mapping.model.DDMFormLayoutRow;
import com.liferay.dynamic.data.mapping.model.DDMStructure;
import com.liferay.dynamic.data.mapping.model.DDMStructureConstants;
import com.liferay.dynamic.data.mapping.model.LocalizedValue;
import com.liferay.dynamic.data.mapping.service.DDMStructureLocalService;
import com.liferay.dynamic.data.mapping.storage.DDMFormFieldValue;
import com.liferay.dynamic.data.mapping.storage.DDMFormValues;
import com.liferay.dynamic.data.mapping.storage.StorageType;
import com.liferay.dynamic.data.mapping.util.DDM;
import com.liferay.journal.model.JournalArticle;
import com.liferay.journal.service.JournalArticleLocalService;
import com.liferay.journal.util.JournalConverter;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.PermissionCheckerFactory;
import com.liferay.portal.kernel.security.permission.PermissionThreadLocal;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.test.util.ServiceContextTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.PropsUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.portal.security.service.access.policy.service.SAPEntryLocalService;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import com.liferay.site.navigation.model.SiteNavigationMenuItem;
import com.liferay.site.navigation.service.SiteNavigationMenuItemLocalService;
import com.liferay.site.navigation.service.SiteNavigationMenuLocalService;
import fi.okm.jod.ohjaaja.cms.navigation.service.NavigationService;
import fi.okm.jod.ohjaaja.cms.sitemap.service.SitemapService;
import fi.okm.jod.ohjaaja.cms.testrunner.client.JodInContainerRunner;
import fi.okm.jod.ohjaaja.cms.util.JodOhjaajaCmsUtil;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;

/**
 * Integration tests for SitemapService. The tests run with an admin permission checker, like a
 * request with a logged-in user's cookies: the sitemap must still show only what the guest sees.
 */
@RunWith(JodInContainerRunner.class)
public class SitemapServiceTest {

  @ClassRule @Rule
  public static final LiferayIntegrationTestRule liferayIntegrationTestRule =
      new LiferayIntegrationTestRule();

  private static final Locale FI = Locale.of("fi", "FI");
  private static final Locale SV = Locale.of("sv", "SE");
  private static final Locale EN = Locale.US;
  private static final String FRONTEND_URL_KEY = "ohjaaja.frontend.url";

  private static BundleContext bundleContext;
  private static SitemapService sitemapService;
  private static JournalArticleLocalService journalArticleLocalService;
  private static DDMStructureLocalService ddmStructureLocalService;
  private static SiteNavigationMenuItemLocalService siteNavigationMenuItemLocalService;
  private static SiteNavigationMenuLocalService siteNavigationMenuLocalService;
  private static PermissionChecker originalPermissionChecker;
  private static DDMStructure structure;
  private static long groupId;
  private static long menuId;
  private static long createdMenuId;
  private static String frontendUrl;
  private static String originalFrontendUrl;

  private final List<SiteNavigationMenuItem> menuItems = new ArrayList<>();
  private final List<JournalArticle> articles = new ArrayList<>();
  private final List<AssetCategory> categories = new ArrayList<>();

  @BeforeClass
  public static void setUpClass() throws Exception {
    bundleContext = FrameworkUtil.getBundle(SitemapServiceTest.class).getBundleContext();
    sitemapService = getService(SitemapService.class);
    journalArticleLocalService = getService(JournalArticleLocalService.class);
    ddmStructureLocalService = getService(DDMStructureLocalService.class);
    siteNavigationMenuItemLocalService = getService(SiteNavigationMenuItemLocalService.class);
    siteNavigationMenuLocalService = getService(SiteNavigationMenuLocalService.class);
    groupId = getService(JodOhjaajaCmsUtil.class).getJodOhjaajaCmsGroup().getGroupId();
    // The test image has no portal-ext.properties of an environment
    originalFrontendUrl = PropsUtil.get(FRONTEND_URL_KEY);
    if (Validator.isBlank(originalFrontendUrl)) {
      PropsUtil.set(FRONTEND_URL_KEY, "http://localhost:8180/ohjaaja");
    }
    frontendUrl = PropsUtil.get(FRONTEND_URL_KEY);

    originalPermissionChecker = PermissionThreadLocal.getPermissionChecker();
    PermissionThreadLocal.setPermissionChecker(
        getService(PermissionCheckerFactory.class).create(TestPropsValues.getUser()));

    var serviceContext = getServiceContext(true);
    var navigation = getService(NavigationService.class).getNavigation(groupId, "fi-FI");
    if (navigation != null) {
      menuId = navigation.id();
    } else {
      createdMenuId =
          siteNavigationMenuLocalService
              .addSiteNavigationMenu(
                  "sitemap-test-menu-" + System.currentTimeMillis(),
                  TestPropsValues.getUserId(),
                  groupId,
                  "Sitemap test menu",
                  serviceContext)
              .getSiteNavigationMenuId();
      menuId = createdMenuId;
    }

    structure = addStructure(serviceContext);
  }

  /** A structure like the article structure of the site, with ingress and content fields. */
  private static DDMStructure addStructure(ServiceContext serviceContext) throws Exception {
    var ddmForm = new DDMForm();
    ddmForm.setAvailableLocales(Set.of(FI, SV, EN));
    ddmForm.setDefaultLocale(FI);
    var layoutRows = new ArrayList<DDMFormLayoutRow>();
    for (var fieldName : List.of("ingress", "content")) {
      var field =
          new DDMFormField(
              fieldName,
              "content".equals(fieldName)
                  ? DDMFormFieldTypeConstants.RICH_TEXT
                  : DDMFormFieldTypeConstants.TEXT);
      field.setLocalizable(true);
      field.setDataType("content".equals(fieldName) ? "html" : "string");
      field.setIndexType("text");
      field.setFieldReference(fieldName);
      var label = new LocalizedValue(FI);
      for (var locale : List.of(FI, SV, EN)) {
        label.addString(locale, fieldName);
      }
      field.setLabel(label);
      ddmForm.addDDMFormField(field);

      var layoutRow = new DDMFormLayoutRow();
      layoutRow.setDDMFormLayoutColumns(List.of(new DDMFormLayoutColumn(12, fieldName)));
      layoutRows.add(layoutRow);
    }

    var layoutPage = new DDMFormLayoutPage();
    layoutPage.setTitle(new LocalizedValue(FI));
    layoutPage.setDescription(new LocalizedValue(FI));
    layoutPage.setDDMFormLayoutRows(layoutRows);
    var layout = new DDMFormLayout();
    layout.setDefaultLocale(FI);
    layout.setDDMFormLayoutPages(List.of(layoutPage));
    layout.setPaginationMode(DDMFormLayout.SINGLE_PAGE_MODE);

    var structureKey = "SITEMAP_TEST_STRUCTURE_" + System.currentTimeMillis();
    return ddmStructureLocalService.addStructure(
        structureKey,
        TestPropsValues.getUserId(),
        groupId,
        0,
        PortalUtil.getClassNameId(JournalArticle.class.getName()),
        structureKey,
        Map.of(FI, "Sitemap test structure"),
        Map.of(),
        ddmForm,
        layout,
        StorageType.DEFAULT.toString(),
        DDMStructureConstants.TYPE_DEFAULT,
        serviceContext);
  }

  @AfterClass
  public static void tearDownClass() throws Exception {
    try {
      if (structure != null) {
        ddmStructureLocalService.deleteStructure(structure);
      }
      if (createdMenuId > 0) {
        siteNavigationMenuLocalService.deleteSiteNavigationMenu(createdMenuId);
      }
    } finally {
      PermissionThreadLocal.setPermissionChecker(originalPermissionChecker);
      if (Validator.isBlank(originalFrontendUrl)) {
        PropsUtil.set(FRONTEND_URL_KEY, "");
      }
      sitemapService.clearCache();
    }
  }

  @After
  public void tearDown() throws Exception {
    for (var menuItem : menuItems) {
      siteNavigationMenuItemLocalService.deleteSiteNavigationMenuItem(menuItem);
    }
    for (var article : articles) {
      // Deletes all versions
      journalArticleLocalService.deleteArticle(
          groupId, article.getArticleId(), getServiceContext(true));
    }
    for (var category : categories) {
      getService(AssetCategoryLocalService.class).deleteCategory(category);
    }
  }

  @Test
  public void shouldCreatePublicServiceAccessPolicyForSitemapApi() throws Exception {
    var sapEntry =
        getService(SAPEntryLocalService.class)
            .fetchSAPEntry(TestPropsValues.getCompanyId(), "JOD_OHJAAJA_SITEMAP");

    Assert.assertNotNull("SAP JOD_OHJAAJA_SITEMAP should exist", sapEntry);
    Assert.assertTrue("SAP should be enabled", sapEntry.isEnabled());
    Assert.assertTrue("SAP should apply to guests", sapEntry.isDefaultSAPEntry());
    Assert.assertEquals(
        "fi.okm.jod.ohjaaja.cms.sitemap.rest.application.SitemapRestApplication#*",
        sapEntry.getAllowedServiceSignatures());
  }

  @Test
  public void shouldListPublishedArticleOnlyInItsLanguages() throws Exception {
    var suffix = suffix();
    var article =
        addArticle(
            "sitemap-test-" + suffix,
            Map.of(FI, "Sitemap testi " + suffix, EN, "Sitemap test " + suffix),
            getServiceContext(true));
    addMenuItem(article);

    var sitemap = getSitemap();
    var contentSitemap = new String(sitemapService.getContentSitemapXml(), StandardCharsets.UTF_8);

    var fiUrl = frontendUrl + "/fi/sitemap-testi-" + suffix;
    var enUrl = frontendUrl + "/en/sitemap-test-" + suffix;
    var lastmod =
        LocalDate.ofInstant(
            Instant.ofEpochMilli(article.getModifiedDate().getTime()),
            ZoneId.of("Europe/Helsinki"));
    Assert.assertTrue(
        sitemap.contains("<loc>" + fiUrl + "</loc><lastmod>" + lastmod + "</lastmod>"));
    Assert.assertTrue(sitemap.contains("<loc>" + enUrl + "</loc>"));
    Assert.assertFalse(sitemap.contains("/sv/sitemap-test"));
    Assert.assertTrue(
        contentSitemap.contains(
            PortalUtil.getPathContext()
                + "/o/jod-sitemap/content/fi/sitemap-test-"
                + suffix
                + "</loc>"));

    var page = sitemapService.getContentPage("fi", article.getExternalReferenceCode());
    Assert.assertTrue(page.isPresent());
    Assert.assertTrue(page.get().contains("<link rel=\"canonical\" href=\"" + fiUrl + "\">"));
    Assert.assertTrue(page.get(), page.get().contains("<p>Sisältö " + suffix + "</p>"));
    Assert.assertTrue(page.get().contains("<p>Ingressi Sitemap testi " + suffix + "</p>"));
    Assert.assertTrue(
        "An untranslated language version should not be found",
        sitemapService.getContentPage("sv", article.getExternalReferenceCode()).isEmpty());
  }

  @Test
  public void shouldShowTheArticlesTagsOnTheContentPage() throws Exception {
    var tagVocabulary =
        getService(AssetVocabularyLocalService.class)
            .fetchAssetVocabularyByExternalReferenceCode("jod-ohjaaja-cms-tags", groupId);
    Assert.assertNotNull("The tag vocabulary should exist", tagVocabulary);
    var suffix = suffix();
    var tag =
        getService(AssetCategoryLocalService.class)
            .addCategory(
                TestPropsValues.getUserId(),
                groupId,
                "Sitemap-tagi " + suffix,
                tagVocabulary.getVocabularyId(),
                getServiceContext(true));
    categories.add(tag);
    var serviceContext = getServiceContext(true);
    serviceContext.setAssetCategoryIds(new long[] {tag.getCategoryId()});
    var article =
        addArticle("sitemap-tagi-" + suffix, Map.of(FI, "Sitemap tagi " + suffix), serviceContext);
    addMenuItem(article);
    sitemapService.clearCache();

    var page = sitemapService.getContentPage("fi", article.getExternalReferenceCode());

    Assert.assertTrue(page.isPresent());
    Assert.assertTrue(
        page.get(),
        page.get().contains("<h2>Avainsanat</h2>\n<ul>\n<li>Sitemap-tagi " + suffix + "</li>"));
  }

  @Test
  public void shouldListTheLatestApprovedVersionOfAnArticleWithManyVersions() throws Exception {
    var suffix = suffix();
    var article =
        addArticle(
            "sitemap-test-versions-" + suffix,
            Map.of(FI, "Sitemap versio vanha " + suffix),
            getServiceContext(true));
    var updated =
        journalArticleLocalService.updateArticle(
            TestPropsValues.getUserId(),
            groupId,
            article.getFolderId(),
            article.getArticleId(),
            article.getVersion(),
            Map.of(FI, "Sitemap versio uusi " + suffix),
            article.getDescriptionMap(),
            article.getContent(),
            article.getLayoutUuid(),
            getServiceContext(true));
    Assert.assertTrue(updated.getVersion() > article.getVersion());
    addMenuItem(article);

    var sitemap = getSitemap();
    var lastmod =
        LocalDate.ofInstant(
            Instant.ofEpochMilli(updated.getModifiedDate().getTime()),
            ZoneId.of("Europe/Helsinki"));

    Assert.assertTrue(
        sitemap,
        sitemap.contains(
            "<loc>"
                + frontendUrl
                + "/fi/sitemap-versio-uusi-"
                + suffix
                + "</loc><lastmod>"
                + lastmod
                + "</lastmod>"));
    Assert.assertFalse(sitemap.contains("sitemap-versio-vanha-" + suffix));
  }

  @Test
  public void shouldServeTheContentPageOfAnArticleWithASlashInItsExternalReferenceCode()
      throws Exception {
    var suffix = suffix();
    var article =
        addArticle(
            "sitemap/test-" + suffix,
            Map.of(FI, "Sitemap kauttaviiva " + suffix),
            getServiceContext(true));
    addMenuItem(article);

    getSitemap();
    var contentSitemap = new String(sitemapService.getContentSitemapXml(), StandardCharsets.UTF_8);
    var contentPath =
        PortalUtil.getPathContext() + "/o/jod-sitemap/content/fi/sitemap/test-" + suffix;
    Assert.assertTrue(contentSitemap, contentSitemap.contains(contentPath + "</loc>"));

    // As a guest over HTTP, through the JAX-RS route
    var response =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(URI.create("http://localhost:8080" + contentPath)).build(),
                HttpResponse.BodyHandlers.ofString());
    Assert.assertEquals(response.body(), 200, response.statusCode());
    Assert.assertTrue(response.body().contains("<h1>Sitemap kauttaviiva " + suffix + "</h1>"));
  }

  @Test
  public void shouldNotListArticleHiddenFromGuestToALoggedInCaller() throws Exception {
    var suffix = suffix();
    var article =
        addArticle(
            "sitemap-test-hidden-" + suffix,
            Map.of(FI, "Sitemap piilotettu " + suffix),
            getServiceContext(false));
    addMenuItem(article);

    Assert.assertFalse(getSitemap().contains("sitemap-piilotettu-" + suffix));
    Assert.assertTrue(
        sitemapService.getContentPage("fi", article.getExternalReferenceCode()).isEmpty());
  }

  @Test
  public void shouldNotListUnpublishedArticle() throws Exception {
    var suffix = suffix();
    var serviceContext = getServiceContext(true);
    serviceContext.setWorkflowAction(WorkflowConstants.ACTION_SAVE_DRAFT);
    var article =
        addArticle(
            "sitemap-test-draft-" + suffix,
            Map.of(FI, "Sitemap luonnos " + suffix),
            serviceContext);
    addMenuItem(article);

    Assert.assertFalse(getSitemap().contains("sitemap-luonnos-" + suffix));
    Assert.assertTrue(
        sitemapService.getContentPage("fi", article.getExternalReferenceCode()).isEmpty());
  }

  @Test
  public void shouldNotFindUnknownArticleOrLanguage() throws Exception {
    sitemapService.clearCache();

    Assert.assertTrue(sitemapService.getContentPage("fi", "no-such-erc-" + suffix()).isEmpty());
    Assert.assertTrue(sitemapService.getContentPage("de", "no-such-erc").isEmpty());
  }

  @Test
  public void shouldRestorePermissionCheckerAfterBuildingTheSitemap() {
    var permissionChecker = PermissionThreadLocal.getPermissionChecker();

    getSitemap();

    Assert.assertSame(permissionChecker, PermissionThreadLocal.getPermissionChecker());
  }

  private String getSitemap() {
    sitemapService.clearCache();
    return new String(sitemapService.getSitemapXml(), StandardCharsets.UTF_8);
  }

  private JournalArticle addArticle(
      String externalReferenceCode, Map<Locale, String> titleMap, ServiceContext serviceContext)
      throws Exception {

    var formValues = new DDMFormValues(structure.getDDMForm());
    formValues.setAvailableLocales(titleMap.keySet());
    formValues.setDefaultLocale(FI);
    var ingress = new LocalizedValue(FI);
    var body = new LocalizedValue(FI);
    titleMap.forEach(
        (locale, title) -> {
          ingress.addString(locale, "Ingressi " + title);
          body.addString(
              locale, "<p>Sisältö " + title.substring(title.lastIndexOf(' ') + 1) + "</p>");
        });
    formValues.addDDMFormFieldValue(fieldValue("ingress", ingress));
    formValues.addDDMFormFieldValue(fieldValue("content", body));
    var content =
        getService(JournalConverter.class)
            .getContent(
                structure,
                getService(DDM.class).getFields(structure.getStructureId(), formValues),
                groupId);

    var article =
        journalArticleLocalService.addArticle(
            externalReferenceCode,
            TestPropsValues.getUserId(),
            groupId,
            0,
            titleMap,
            null,
            content,
            structure.getStructureId(),
            null,
            serviceContext);
    articles.add(article);
    return article;
  }

  private static DDMFormFieldValue fieldValue(String name, LocalizedValue value) {
    var fieldValue = new DDMFormFieldValue();
    fieldValue.setName(name);
    fieldValue.setFieldReference(name);
    fieldValue.setInstanceId(StringUtil.randomString());
    fieldValue.setValue(value);
    return fieldValue;
  }

  private void addMenuItem(JournalArticle article) throws Exception {
    var className = JournalArticle.class.getName();
    menuItems.add(
        siteNavigationMenuItemLocalService.addSiteNavigationMenuItem(
            "sitemap-test-item-" + article.getExternalReferenceCode(),
            TestPropsValues.getUserId(),
            groupId,
            menuId,
            0,
            className,
            "className="
                + className
                + "\nclassPK="
                + article.getResourcePrimKey()
                + "\ntitle="
                + article.getExternalReferenceCode()
                + "\n",
            getServiceContext(true)));
  }

  private static ServiceContext getServiceContext(boolean addGuestPermissions) throws Exception {
    var serviceContext =
        ServiceContextTestUtil.getServiceContext(groupId, TestPropsValues.getUserId());
    serviceContext.setAddGroupPermissions(true);
    serviceContext.setAddGuestPermissions(addGuestPermissions);
    return serviceContext;
  }

  private static String suffix() {
    return String.valueOf(System.nanoTime());
  }

  private static <T> T getService(Class<T> serviceClass) {
    var reference = bundleContext.getServiceReference(serviceClass);
    if (reference == null) {
      throw new IllegalStateException("Service not found: " + serviceClass.getName());
    }
    return bundleContext.getService(reference);
  }
}
