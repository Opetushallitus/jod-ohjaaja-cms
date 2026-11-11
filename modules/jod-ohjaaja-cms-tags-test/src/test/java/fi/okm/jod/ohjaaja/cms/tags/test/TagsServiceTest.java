/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.tags.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.asset.kernel.model.AssetVocabulary;
import com.liferay.asset.kernel.service.AssetVocabularyLocalService;
import com.liferay.portal.kernel.model.ResourceConstants;
import com.liferay.portal.kernel.model.role.RoleConstants;
import com.liferay.portal.kernel.security.permission.ActionKeys;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.service.ResourcePermissionLocalService;
import com.liferay.portal.kernel.service.RoleLocalService;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.security.service.access.policy.service.SAPEntryLocalService;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import fi.okm.jod.ohjaaja.cms.tags.dto.JodTaxonomyCategoryDto;
import fi.okm.jod.ohjaaja.cms.tags.exception.TagsServiceException;
import fi.okm.jod.ohjaaja.cms.tags.service.TagsService;
import fi.okm.jod.ohjaaja.cms.util.JodOhjaajaCmsUtil;
import fi.okm.jod.ohjaaja.cms.util.ServiceAccessPolicyInitializer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Integration tests for TagsService. Tests taxonomy category management functionality. */
@RunWith(Arquillian.class)
public class TagsServiceTest {

  @ClassRule @Rule
  public static final AggregateTestRule aggregateTestRule = new LiferayIntegrationTestRule();

  @Inject private static TagsService tagsService;
  @Inject private static JodOhjaajaCmsUtil jodOhjaajaCmsUtil;
  @Inject private static AssetVocabularyLocalService assetVocabularyLocalService;
  @Inject private static GroupLocalService groupLocalService;
  @Inject private static RoleLocalService roleLocalService;
  @Inject private static ResourcePermissionLocalService resourcePermissionLocalService;
  @Inject private static SAPEntryLocalService sapEntryLocalService;
  @Inject private static ServiceAccessPolicyInitializer serviceAccessPolicyInitializer;

  private static Long TEST_GROUP_ID;

  @BeforeClass
  public static void setUpClass() {
    TEST_GROUP_ID = jodOhjaajaCmsUtil.getJodOhjaajaCmsGroup().getGroupId();
  }

  @Test
  public void shouldGrantGuestViewPermissionOnTagVocabulary() throws Exception {
    // A guest can view a tag only if it can view the vocabulary
    assertGuestCanViewVocabulary(
        assetVocabularyLocalService.getAssetVocabularyByExternalReferenceCode(
            "jod-ohjaaja-cms-tags", TEST_GROUP_ID));
  }

  @Test
  public void shouldCreateTagVocabularyWhenMissing() throws Exception {
    // Hides the vocabulary of the container from the service, as in an empty environment
    var existingVocabulary = hideTagVocabulary(true);
    AssetVocabulary createdVocabulary = null;

    try {
      tagsService.initTagVocabulary();

      createdVocabulary =
          assetVocabularyLocalService.fetchAssetVocabularyByExternalReferenceCode(
              "jod-ohjaaja-cms-tags", TEST_GROUP_ID);
      Assert.assertNotNull("The tag vocabulary should be created", createdVocabulary);
      Assert.assertNotEquals(
          existingVocabulary.getVocabularyId(), createdVocabulary.getVocabularyId());
      Assert.assertEquals("Avainsanat", createdVocabulary.getTitle("fi_FI"));
      Assert.assertTrue(createdVocabulary.isMultiValued());
      assertGuestCanViewVocabulary(createdVocabulary);

      // Nothing is created again
      tagsService.initTagVocabulary();
      Assert.assertEquals(
          createdVocabulary.getVocabularyId(),
          assetVocabularyLocalService
              .getAssetVocabularyByExternalReferenceCode("jod-ohjaaja-cms-tags", TEST_GROUP_ID)
              .getVocabularyId());
    } finally {
      if (createdVocabulary != null) {
        assetVocabularyLocalService.deleteVocabulary(createdVocabulary);
      }
      restoreTagVocabulary(existingVocabulary);
    }
  }

  @Test
  public void shouldTakeOverTagVocabularyWithoutExternalReferenceCode() throws Exception {
    // Like a vocabulary created before it had a fixed external reference code
    var existingVocabulary = hideTagVocabulary(false);

    try {
      tagsService.initTagVocabulary();

      var takenOverVocabulary =
          assetVocabularyLocalService.fetchAssetVocabularyByExternalReferenceCode(
              "jod-ohjaaja-cms-tags", TEST_GROUP_ID);
      Assert.assertNotNull(takenOverVocabulary);
      Assert.assertEquals(
          existingVocabulary.getVocabularyId(), takenOverVocabulary.getVocabularyId());
    } finally {
      restoreTagVocabulary(existingVocabulary);
    }
  }

  /** Changes the external reference code, and optionally the name, of the tag vocabulary. */
  private static AssetVocabulary hideTagVocabulary(boolean rename) throws Exception {
    var vocabulary =
        assetVocabularyLocalService.getAssetVocabularyByExternalReferenceCode(
            "jod-ohjaaja-cms-tags", TEST_GROUP_ID);
    var suffix = System.currentTimeMillis();
    vocabulary.setExternalReferenceCode("hidden-tags-" + suffix);
    if (rename) {
      // The vocabulary is also taken over by its name or title
      vocabulary.setName("hidden-tags-" + suffix);
      vocabulary.setTitleMap(Map.of(LocaleUtil.fromLanguageId("fi_FI"), "Piilotettu " + suffix));
    }
    return assetVocabularyLocalService.updateAssetVocabulary(vocabulary);
  }

  private static void restoreTagVocabulary(AssetVocabulary hiddenVocabulary) throws Exception {
    // Fetched again: an updated vocabulary has a new MVCC version
    var vocabulary =
        assetVocabularyLocalService.getAssetVocabulary(hiddenVocabulary.getVocabularyId());
    vocabulary.setExternalReferenceCode("jod-ohjaaja-cms-tags");
    vocabulary.setName("tags");
    vocabulary.setTitleMap(
        Map.of(
            LocaleUtil.fromLanguageId("fi_FI"), "Avainsanat",
            LocaleUtil.fromLanguageId("en_US"), "Tags",
            LocaleUtil.fromLanguageId("sv_SE"), "Taggar"));
    assetVocabularyLocalService.updateAssetVocabulary(vocabulary);
  }

  @Test
  public void shouldReturnNoTagsForSiteWithoutTagVocabulary() throws Exception {
    Assert.assertTrue(tagsService.getJodTaxonomyCategories(getCompanyGroupId()).isEmpty());
  }

  @Test
  public void shouldNameMissingTagVocabularyWhenAddingTag() throws Exception {
    var companyGroupId = getCompanyGroupId();

    var exception =
        Assert.assertThrows(
            TagsServiceException.class,
            () ->
                tagsService.addOrUpdateJodTaxonomyCategory(
                    null, "test-no-vocabulary", "Test", Map.of("fi_FI", "Testi"), companyGroupId));

    Assert.assertTrue(
        exception.getMessage(), exception.getMessage().contains("jod-ohjaaja-cms-tags"));
  }

  private static long getCompanyGroupId() throws Exception {
    return groupLocalService.getCompanyGroup(TestPropsValues.getCompanyId()).getGroupId();
  }

  private static void assertGuestCanViewVocabulary(AssetVocabulary vocabulary) throws Exception {
    var guestRole = roleLocalService.getRole(vocabulary.getCompanyId(), RoleConstants.GUEST);
    var resourcePermission =
        resourcePermissionLocalService.fetchResourcePermission(
            vocabulary.getCompanyId(),
            AssetVocabulary.class.getName(),
            ResourceConstants.SCOPE_INDIVIDUAL,
            String.valueOf(vocabulary.getVocabularyId()),
            guestRole.getRoleId());

    Assert.assertNotNull(
        "The guest should have permissions on the tag vocabulary", resourcePermission);
    Assert.assertTrue(
        "The guest should be able to view the tag vocabulary",
        resourcePermission.hasActionId(ActionKeys.VIEW));
  }

  @Test
  public void shouldCreatePublicServiceAccessPolicyForTagsApi() throws Exception {
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
    Assert.assertEquals(200, guestGet(base + "/jod-navigation"));
    // The deprecated path that is kept until the UI uses the path without the site ID
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
