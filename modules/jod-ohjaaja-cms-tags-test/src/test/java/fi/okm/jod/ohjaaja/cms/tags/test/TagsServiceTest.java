package fi.okm.jod.ohjaaja.cms.tags.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import fi.okm.jod.ohjaaja.cms.tags.dto.JodTaxonomyCategoryDto;
import fi.okm.jod.ohjaaja.cms.tags.service.TagsService;
import java.util.List;
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

/**
 * Integration tests for TagsService.
 * Tests taxonomy category management functionality.
 */
@RunWith(Arquillian.class)
public class TagsServiceTest {

  @ClassRule @Rule
  public static final AggregateTestRule aggregateTestRule = new LiferayIntegrationTestRule();

  private static TagsService tagsService;
  private static BundleContext bundleContext;
  private static ServiceReference<TagsService> serviceReference;
  private static final long TEST_SITE_ID = 20117L;

  @BeforeClass
  public static void setUpClass() {
    var bundle = FrameworkUtil.getBundle(TagsServiceTest.class);
    bundleContext = bundle.getBundleContext();
    serviceReference = bundleContext.getServiceReference(TagsService.class);
    if (serviceReference != null) {
      tagsService = bundleContext.getService(serviceReference);
    }
  }

  @AfterClass
  public static void tearDownClass() {
    if (serviceReference != null && bundleContext != null) {
      bundleContext.ungetService(serviceReference);
    }
  }

  @Test
  public void shouldCreateNewTaxonomyCategory() {
    var testERC = "test-category-" + System.currentTimeMillis();
    var testName = "Test Category";
    var translations = Map.of("en_US", "Test Category", "fi_FI", "Testiluokka");

    System.out.println("Creating new category: " + testERC);

    tagsService.addOrUpdateJodTaxonomyCategory(null, testERC, testName, translations, TEST_SITE_ID);

    List<JodTaxonomyCategoryDto> categories = tagsService.getJodTaxonomyCategories(TEST_SITE_ID);
    boolean found = categories.stream()
        .anyMatch(cat -> testERC.equals(cat.externalReferenceCode()));

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
        null, testERC, initialName, Map.of("en_US", initialName), TEST_SITE_ID);

    // Find created category
    List<JodTaxonomyCategoryDto> categories = tagsService.getJodTaxonomyCategories(TEST_SITE_ID);
    JodTaxonomyCategoryDto createdCategory = categories.stream()
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
        TEST_SITE_ID);

    // Verify update
    categories = tagsService.getJodTaxonomyCategories(TEST_SITE_ID);
    JodTaxonomyCategoryDto updatedCategory = categories.stream()
        .filter(cat -> testERC.equals(cat.externalReferenceCode()))
        .findFirst()
        .orElse(null);

    Assert.assertNotNull("Updated category should exist", updatedCategory);
    Assert.assertEquals("Name should be updated", updatedName, updatedCategory.name());
    Assert.assertEquals("Category ID should remain same", 
        createdCategory.id(), updatedCategory.id());
    System.out.println("✅ Category updated successfully");
  }

  @Test
  public void shouldGetAllTaxonomyCategories() {
    var testERC1 = "test-list-1-" + System.currentTimeMillis();
    var testERC2 = "test-list-2-" + System.currentTimeMillis();

    System.out.println("Creating categories for list test");
    
    int initialCount = tagsService.getJodTaxonomyCategories(TEST_SITE_ID).size();
    
    tagsService.addOrUpdateJodTaxonomyCategory(
        null, testERC1, "Category 1", Map.of("en_US", "Category 1"), TEST_SITE_ID);
    tagsService.addOrUpdateJodTaxonomyCategory(
        null, testERC2, "Category 2", Map.of("en_US", "Category 2"), TEST_SITE_ID);

    List<JodTaxonomyCategoryDto> categories = tagsService.getJodTaxonomyCategories(TEST_SITE_ID);

    Assert.assertNotNull("Categories list should not be null", categories);
    Assert.assertTrue("Should have at least 2 more categories", 
        categories.size() >= initialCount + 2);
    
    boolean found1 = categories.stream().anyMatch(c -> testERC1.equals(c.externalReferenceCode()));
    boolean found2 = categories.stream().anyMatch(c -> testERC2.equals(c.externalReferenceCode()));
    
    Assert.assertTrue("First category should be in list", found1);
    Assert.assertTrue("Second category should be in list", found2);
    
    System.out.println("✅ Found " + categories.size() + " categories total");
  }

  @Test
  public void shouldHandleMultilingualNames() {
    var testERC = "test-i18n-" + System.currentTimeMillis();
    var translations = Map.of(
        "en_US", "English Name",
        "fi_FI", "Suomalainen Nimi",
        "sv_SE", "Svenskt Namn"
    );

    System.out.println("Creating multilingual category: " + testERC);

    tagsService.addOrUpdateJodTaxonomyCategory(
        null, testERC, "English Name", translations, TEST_SITE_ID);

    List<JodTaxonomyCategoryDto> categories = tagsService.getJodTaxonomyCategories(TEST_SITE_ID);
    JodTaxonomyCategoryDto category = categories.stream()
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
        null, testERC, "Original", Map.of("en_US", "Original"), TEST_SITE_ID);

    List<JodTaxonomyCategoryDto> categories = tagsService.getJodTaxonomyCategories(TEST_SITE_ID);
    JodTaxonomyCategoryDto original = categories.stream()
        .filter(cat -> testERC.equals(cat.externalReferenceCode()))
        .findFirst()
        .orElseThrow();

    // Update with same ERC
    tagsService.addOrUpdateJodTaxonomyCategory(
        original.id(), testERC, "Modified", Map.of("en_US", "Modified"), TEST_SITE_ID);

    categories = tagsService.getJodTaxonomyCategories(TEST_SITE_ID);
    JodTaxonomyCategoryDto updated = categories.stream()
        .filter(cat -> testERC.equals(cat.externalReferenceCode()))
        .findFirst()
        .orElseThrow();

    Assert.assertEquals("ERC should remain unchanged", testERC, updated.externalReferenceCode());
    Assert.assertEquals("Name should be updated", "Modified", updated.name());
    Assert.assertEquals("ID should remain same", original.id(), updated.id());
    System.out.println("✅ Category updated preserving ERC");
  }
}
