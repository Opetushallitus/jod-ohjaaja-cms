/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.tags.test;

import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.security.service.access.policy.service.SAPEntryLocalService;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import fi.okm.jod.ohjaaja.cms.tags.dto.JodTaxonomyCategoryDto;
import fi.okm.jod.ohjaaja.cms.tags.service.TagsService;
import fi.okm.jod.ohjaaja.cms.testrunner.client.JodInContainerRunner;
import fi.okm.jod.ohjaaja.cms.util.JodOhjaajaCmsUtil;
import fi.okm.jod.ohjaaja.cms.util.ServiceAccessPolicyInitializer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

/** Integration tests for TagsService. Tests taxonomy category management functionality. */
@RunWith(JodInContainerRunner.class)
public class TagsServiceTest {

  @ClassRule @Rule
  public static final AggregateTestRule aggregateTestRule = new LiferayIntegrationTestRule();

  private static TagsService tagsService;
  private static BundleContext bundleContext;
  private static ServiceReference<TagsService> serviceReference;
  private static Long TEST_GROUP_ID;

  @BeforeClass
  public static void setUpClass() {
    var bundle = FrameworkUtil.getBundle(TagsServiceTest.class);
    bundleContext = bundle.getBundleContext();
    serviceReference = bundleContext.getServiceReference(TagsService.class);
    if (serviceReference != null) {
      tagsService = bundleContext.getService(serviceReference);
    }
    var jodOhjaajaCmsUtil =
        bundleContext.getService(bundleContext.getServiceReference(JodOhjaajaCmsUtil.class));
    TEST_GROUP_ID = jodOhjaajaCmsUtil.getJodOhjaajaCmsGroup().getGroupId();
  }

  @AfterClass
  public static void tearDownClass() {
    if (serviceReference != null && bundleContext != null) {
      bundleContext.ungetService(serviceReference);
    }
  }

  @Test
  public void shouldCreatePublicServiceAccessPolicyForTagsApi() throws Exception {
    var sapEntryLocalService =
        bundleContext.getService(bundleContext.getServiceReference(SAPEntryLocalService.class));

    var sapEntry =
        sapEntryLocalService.fetchSAPEntry(TestPropsValues.getCompanyId(), "JOD_OHJAAJA_TAGS");

    Assert.assertNotNull("SAP JOD_OHJAAJA_TAGS should exist", sapEntry);
    Assert.assertTrue("SAP should be enabled", sapEntry.isEnabled());
    Assert.assertTrue("SAP should apply to guests", sapEntry.isDefaultSAPEntry());
    Assert.assertEquals(
        "fi.okm.jod.ohjaaja.cms.tags.rest.application.TagsRestApplication#*",
        sapEntry.getAllowedServiceSignatures());
  }

  @Test
  public void shouldAllowOnlyFrontendHeadlessDeliveryEndpointsForGuests() throws Exception {
    var sapEntryLocalService =
        bundleContext.getService(bundleContext.getServiceReference(SAPEntryLocalService.class));

    var sapEntry =
        sapEntryLocalService.fetchSAPEntry(TestPropsValues.getCompanyId(), "HEADLESS_ACCESS");

    Assert.assertNotNull("SAP HEADLESS_ACCESS should exist", sapEntry);
    Assert.assertTrue("SAP should be enabled", sapEntry.isEnabled());
    Assert.assertTrue("SAP should apply to guests", sapEntry.isDefaultSAPEntry());
    var resource =
        "com.liferay.headless.delivery.internal.resource.v1_0.StructuredContentResourceImpl";
    Assert.assertEquals(
        Set.of(resource + "#getSiteStructuredContentsPage", resource + "#getStructuredContent"),
        Set.copyOf(sapEntry.getAllowedServiceSignatures().lines().toList()));
  }

  @Test
  public void shouldServePublicApisToGuestsAndBlockOthers() throws Exception {
    var base = "http://localhost:8080/o";
    var sites = base + "/headless-delivery/v1.0/sites/" + TEST_GROUP_ID;

    Assert.assertEquals(200, guestGet(sites + "/structured-contents"));
    Assert.assertEquals(200, guestGet(base + "/jod-tags/" + TEST_GROUP_ID));
    Assert.assertEquals(200, guestGet(base + "/jod-navigation/" + TEST_GROUP_ID));
    Assert.assertEquals(403, guestGet(sites + "/structured-contents/by-key/does-not-exist"));
  }

  private static int guestGet(String url) throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create(url)).header("Accept", "application/json").GET().build();
    var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    System.out.println("GET " + url + " -> " + response.statusCode());
    return response.statusCode();
  }

  @Test
  public void shouldCreateServiceAccessPolicyWhenDefaultLocaleIsFinnish() throws Exception {
    var sapEntryLocalService =
        bundleContext.getService(bundleContext.getServiceReference(SAPEntryLocalService.class));
    var serviceAccessPolicyInitializer =
        bundleContext.getService(
            bundleContext.getServiceReference(ServiceAccessPolicyInitializer.class));
    var name = "JOD_OHJAAJA_TEST_FI_" + System.currentTimeMillis();
    var originalDefault = LocaleUtil.getDefault();

    // Real environments use company.default.locale=fi_FI, the test container uses en_US
    LocaleUtil.setDefault("fi", "FI", "");
    try {
      serviceAccessPolicyInitializer.initPublicServiceAccessPolicy(name, "a.B#c", "Test");
      serviceAccessPolicyInitializer.initPublicServiceAccessPolicy(name, "a.B#d", "Test");

      var sapEntry = sapEntryLocalService.fetchSAPEntry(TestPropsValues.getCompanyId(), name);
      Assert.assertNotNull(sapEntry);
      Assert.assertEquals("a.B#d", sapEntry.getAllowedServiceSignatures());
      sapEntryLocalService.deleteSAPEntry(sapEntry.getSapEntryId());
    } finally {
      LocaleUtil.setDefault(
          originalDefault.getLanguage(),
          originalDefault.getCountry(),
          originalDefault.getVariant());
    }
  }

  @Test
  public void shouldNotRewriteServiceAccessPolicyWhenOnlyFormattingDiffers() throws Exception {
    var sapEntryLocalService =
        bundleContext.getService(bundleContext.getServiceReference(SAPEntryLocalService.class));
    var serviceAccessPolicyInitializer =
        bundleContext.getService(
            bundleContext.getServiceReference(ServiceAccessPolicyInitializer.class));
    var name = "JOD_OHJAAJA_TEST_" + System.currentTimeMillis();

    serviceAccessPolicyInitializer.initPublicServiceAccessPolicy(name, "a.B#c\na.B#d", "Test");
    var sapEntry = sapEntryLocalService.fetchSAPEntry(TestPropsValues.getCompanyId(), name);
    try {
      sapEntry.setAllowedServiceSignatures("a.B#d\r\n\r\na.B#c\r\n");
      sapEntryLocalService.updateSAPEntry(sapEntry);

      serviceAccessPolicyInitializer.initPublicServiceAccessPolicy(name, "a.B#c\na.B#d", "Test");

      Assert.assertEquals(
          "a.B#d\r\n\r\na.B#c\r\n",
          sapEntryLocalService
              .fetchSAPEntry(TestPropsValues.getCompanyId(), name)
              .getAllowedServiceSignatures());
    } finally {
      sapEntryLocalService.deleteSAPEntry(sapEntry.getSapEntryId());
    }
  }

  @Test
  public void shouldOverwriteModifiedServiceAccessPolicy() throws Exception {
    var sapEntryLocalService =
        bundleContext.getService(bundleContext.getServiceReference(SAPEntryLocalService.class));
    var serviceAccessPolicyInitializer =
        bundleContext.getService(
            bundleContext.getServiceReference(ServiceAccessPolicyInitializer.class));
    var name = "JOD_OHJAAJA_TEST_" + System.currentTimeMillis();
    var signature = TagsServiceTest.class.getName() + "#*";

    serviceAccessPolicyInitializer.initPublicServiceAccessPolicy(name, signature, "Test");
    var sapEntry = sapEntryLocalService.fetchSAPEntry(TestPropsValues.getCompanyId(), name);
    try {
      sapEntry.setEnabled(false);
      sapEntry.setAllowedServiceSignatures("com.example.Other#*");
      sapEntryLocalService.updateSAPEntry(sapEntry);

      serviceAccessPolicyInitializer.initPublicServiceAccessPolicy(name, signature, "Test");

      var restored = sapEntryLocalService.fetchSAPEntry(TestPropsValues.getCompanyId(), name);
      Assert.assertEquals(sapEntry.getSapEntryId(), restored.getSapEntryId());
      Assert.assertTrue(restored.isEnabled());
      Assert.assertTrue(restored.isDefaultSAPEntry());
      Assert.assertEquals(signature, restored.getAllowedServiceSignatures());
    } finally {
      sapEntryLocalService.deleteSAPEntry(sapEntry.getSapEntryId());
    }
  }

  @Test
  public void shouldCreateNewTaxonomyCategory() {
    var testERC = "test-category-" + System.currentTimeMillis();
    var testName = "Test Category";
    var translations = Map.of("en_US", "Test Category", "fi_FI", "Testiluokka");

    System.out.println("Creating new category: " + testERC);

    tagsService.addOrUpdateJodTaxonomyCategory(
        null, testERC, testName, translations, TEST_GROUP_ID);

    List<JodTaxonomyCategoryDto> categories = tagsService.getJodTaxonomyCategories(TEST_GROUP_ID);
    boolean found =
        categories.stream().anyMatch(cat -> testERC.equals(cat.externalReferenceCode()));

    Assert.assertTrue("Created category should be found in the list", found);
    System.out.println("✅ Category created successfully");
  }

  @Test
  public void shouldUpdateExistingTaxonomyCategory() {
    var testERC = "test-update-" + System.currentTimeMillis();
    var initialName = "Initial Name";
    var updatedName = "Updated Name";

    // Create category
    System.out.println("Creating category for update test: " + testERC);
    tagsService.addOrUpdateJodTaxonomyCategory(
        null, testERC, initialName, Map.of("en_US", initialName), TEST_GROUP_ID);

    // Find created category
    List<JodTaxonomyCategoryDto> categories = tagsService.getJodTaxonomyCategories(TEST_GROUP_ID);
    JodTaxonomyCategoryDto createdCategory =
        categories.stream()
            .filter(cat -> testERC.equals(cat.externalReferenceCode()))
            .findFirst()
            .orElse(null);

    Assert.assertNotNull("Category should be created", createdCategory);
    Assert.assertEquals("Initial name should match", initialName, createdCategory.name());

    // Update category
    System.out.println("Updating category: " + testERC);
    tagsService.addOrUpdateJodTaxonomyCategory(
        createdCategory.id(),
        testERC,
        updatedName,
        Map.of("en_US", updatedName, "fi_FI", "Päivitetty"),
        TEST_GROUP_ID);

    // Verify update
    categories = tagsService.getJodTaxonomyCategories(TEST_GROUP_ID);
    JodTaxonomyCategoryDto updatedCategory =
        categories.stream()
            .filter(cat -> testERC.equals(cat.externalReferenceCode()))
            .findFirst()
            .orElse(null);

    Assert.assertNotNull("Updated category should exist", updatedCategory);
    Assert.assertEquals("Name should be updated", updatedName, updatedCategory.name());
    Assert.assertEquals(
        "Category ID should remain same", createdCategory.id(), updatedCategory.id());
    System.out.println("✅ Category updated successfully");
  }

  @Test
  public void shouldGetAllTaxonomyCategories() {
    var testERC1 = "test-list-1-" + System.currentTimeMillis();
    var testERC2 = "test-list-2-" + System.currentTimeMillis();

    System.out.println("Creating categories for list test");

    int initialCount = tagsService.getJodTaxonomyCategories(TEST_GROUP_ID).size();

    tagsService.addOrUpdateJodTaxonomyCategory(
        null, testERC1, "Category 1", Map.of("en_US", "Category 1"), TEST_GROUP_ID);
    tagsService.addOrUpdateJodTaxonomyCategory(
        null, testERC2, "Category 2", Map.of("en_US", "Category 2"), TEST_GROUP_ID);

    List<JodTaxonomyCategoryDto> categories = tagsService.getJodTaxonomyCategories(TEST_GROUP_ID);

    Assert.assertNotNull("Categories list should not be null", categories);
    Assert.assertTrue(
        "Should have at least 2 more categories", categories.size() >= initialCount + 2);

    boolean found1 = categories.stream().anyMatch(c -> testERC1.equals(c.externalReferenceCode()));
    boolean found2 = categories.stream().anyMatch(c -> testERC2.equals(c.externalReferenceCode()));

    Assert.assertTrue("First category should be in list", found1);
    Assert.assertTrue("Second category should be in list", found2);

    System.out.println("✅ Found " + categories.size() + " categories total");
  }

  @Test
  public void shouldHandleMultilingualNames() {
    var testERC = "test-i18n-" + System.currentTimeMillis();
    var translations =
        Map.of(
            "en_US", "English Name",
            "fi_FI", "Suomalainen Nimi",
            "sv_SE", "Svenskt Namn");

    System.out.println("Creating multilingual category: " + testERC);

    tagsService.addOrUpdateJodTaxonomyCategory(
        null, testERC, "English Name", translations, TEST_GROUP_ID);

    List<JodTaxonomyCategoryDto> categories = tagsService.getJodTaxonomyCategories(TEST_GROUP_ID);
    JodTaxonomyCategoryDto category =
        categories.stream()
            .filter(cat -> testERC.equals(cat.externalReferenceCode()))
            .findFirst()
            .orElse(null);

    Assert.assertNotNull("Multilingual category should be created", category);
    Assert.assertEquals("Default name should match", "English Name", category.name());
    System.out.println("✅ Multilingual category created");
  }

  @Test
  public void shouldUpdateWithoutChangingExternalReferenceCode() {
    var testERC = "test-erc-stable-" + System.currentTimeMillis();

    // Create category
    tagsService.addOrUpdateJodTaxonomyCategory(
        null, testERC, "Original", Map.of("en_US", "Original"), TEST_GROUP_ID);

    List<JodTaxonomyCategoryDto> categories = tagsService.getJodTaxonomyCategories(TEST_GROUP_ID);
    JodTaxonomyCategoryDto original =
        categories.stream()
            .filter(cat -> testERC.equals(cat.externalReferenceCode()))
            .findFirst()
            .orElseThrow();

    // Update with same ERC
    tagsService.addOrUpdateJodTaxonomyCategory(
        original.id(), testERC, "Modified", Map.of("en_US", "Modified"), TEST_GROUP_ID);

    categories = tagsService.getJodTaxonomyCategories(TEST_GROUP_ID);
    JodTaxonomyCategoryDto updated =
        categories.stream()
            .filter(cat -> testERC.equals(cat.externalReferenceCode()))
            .findFirst()
            .orElseThrow();

    Assert.assertEquals("ERC should remain unchanged", testERC, updated.externalReferenceCode());
    Assert.assertEquals("Name should be updated", "Modified", updated.name());
    Assert.assertEquals("ID should remain same", original.id(), updated.id());
    System.out.println("✅ Category updated preserving ERC");
  }
}
