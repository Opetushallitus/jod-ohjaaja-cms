/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.search.test;

import fi.okm.jod.ohjaaja.cms.testrunner.client.JodInContainerRunner;
import com.liferay.portal.kernel.search.Field;
import com.liferay.portal.kernel.search.SearchContext;
import com.liferay.portal.kernel.search.Sort;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.PermissionCheckerFactory;
import com.liferay.portal.kernel.security.permission.PermissionThreadLocal;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.search.spi.model.query.contributor.SearchContextContributor;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import fi.okm.jod.ohjaaja.cms.search.JodJournalArticleSearchContextContributor;
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
 * Integration tests for JodJournalArticleSearchContextContributor.
 *
 * Verifies the OSGi service registration as well as the contribute() behavior
 * - score-based sorting replaces the default Vulcan ENTRY_CLASS_PK sort when keywords are present
 * - score is enabled on the QueryConfig when keywords are present
 * - searches without keywords are left untouched
 * - non-default sorts are preserved (scoring is still enabled)
 */
@RunWith(JodInContainerRunner.class)
public class JodJournalArticleSearchContextContributorTest {

  @ClassRule @Rule
  public static final LiferayIntegrationTestRule liferayIntegrationTestRule =
      new LiferayIntegrationTestRule();

  private static final String JOURNAL_ARTICLE_CLASS_NAME =
      "com.liferay.journal.model.JournalArticle";

  private static BundleContext bundleContext;
  private static PermissionChecker originalPermissionChecker;
  private static JodJournalArticleSearchContextContributor contributor;
  private static ServiceReference<?> contributorReference;

  @BeforeClass
  public static void setUpClass() throws Exception {
    var bundle = FrameworkUtil.getBundle(JodJournalArticleSearchContextContributorTest.class);
    bundleContext = bundle.getBundleContext();

    originalPermissionChecker = PermissionThreadLocal.getPermissionChecker();
    var permissionCheckerFactoryRef =
        bundleContext.getServiceReference(PermissionCheckerFactory.class);
    var permissionCheckerFactory = bundleContext.getService(permissionCheckerFactoryRef);
    var adminUser = TestPropsValues.getUser();
    var permissionChecker = permissionCheckerFactory.create(adminUser);
    PermissionThreadLocal.setPermissionChecker(permissionChecker);
    bundleContext.ungetService(permissionCheckerFactoryRef);

    contributor = lookupContributor();
    Assert.assertNotNull(
        "JodJournalArticleSearchContextContributor must be registered for JournalArticle",
        contributor);
  }

  @AfterClass
  public static void tearDownClass() {
    PermissionThreadLocal.setPermissionChecker(originalPermissionChecker);

    if (contributorReference != null && bundleContext != null) {
      bundleContext.ungetService(contributorReference);
      contributorReference = null;
    }
  }

  private static JodJournalArticleSearchContextContributor lookupContributor() throws Exception {
    var references =
        bundleContext.getAllServiceReferences(
            SearchContextContributor.class.getName(),
            "(indexer.class.name=" + JOURNAL_ARTICLE_CLASS_NAME + ")");
    if (references == null) {
      return null;
    }

    for (ServiceReference<?> ref : references) {
      var service = bundleContext.getService(ref);
      if (service instanceof JodJournalArticleSearchContextContributor jodContributor) {
        contributorReference = ref;
        return jodContributor;
      }
      bundleContext.ungetService(ref);
    }
    return null;
  }

  @Test
  public void shouldHaveSearchContextContributorRegistered() throws Exception {
    var references =
        bundleContext.getAllServiceReferences(
            SearchContextContributor.class.getName(),
            "(indexer.class.name=" + JOURNAL_ARTICLE_CLASS_NAME + ")");

    Assert.assertNotNull("Should have SearchContextContributor services", references);
    Assert.assertTrue(
        "Should have at least one SearchContextContributor for JournalArticle",
        references.length > 0);

    var found = false;
    for (ServiceReference<?> ref : references) {
      var service = bundleContext.getService(ref);
      try {
        if (service instanceof JodJournalArticleSearchContextContributor) {
          found = true;

          var indexerClassName = ref.getProperty("indexer.class.name");
          Assert.assertEquals(
              "Should be configured for JournalArticle",
              JOURNAL_ARTICLE_CLASS_NAME,
              indexerClassName);
          break;
        }
      } finally {
        bundleContext.ungetService(ref);
      }
    }

    Assert.assertTrue(
        "JodJournalArticleSearchContextContributor should be registered", found);
  }

  @Test
  public void shouldReplaceDefaultVulcanSortWithScoreSort() {
    var searchContext = createSearchContext("some keywords");
    searchContext.setSorts(defaultVulcanSort());

    contributor.contribute(searchContext, null);

    Assert.assertTrue(
        "Score should be enabled in query config when keywords are present",
        searchContext.getQueryConfig().isScoreEnabled());

    var sorts = searchContext.getSorts();
    Assert.assertNotNull("Sorts should not be null", sorts);
    Assert.assertEquals("Exactly one sort should be configured", 1, sorts.length);

    var scoreSort = sorts[0];
    Assert.assertNull("Score sort field name should be null", scoreSort.getFieldName());
    Assert.assertEquals(
        "Score sort should be SCORE_TYPE", Sort.SCORE_TYPE, scoreSort.getType());
    Assert.assertFalse("Score sort should not be reversed", scoreSort.isReverse());
  }

  @Test
  public void shouldNotChangeSortWhenKeywordsAreBlank() {
    var searchContext = createSearchContext(null);
    var originalSorts = defaultVulcanSort();
    searchContext.setSorts(originalSorts);

    contributor.contribute(searchContext, null);

    // The contributor exits early when there are no keywords, so the default Vulcan sort
    // must remain intact. Note: QueryConfig.isScoreEnabled() defaults to true in Liferay, so
    // we cannot assert anything about its state here - only that the sorts are not replaced
    // with a score-based sort.
    var sorts = searchContext.getSorts();
    Assert.assertEquals("Sorts should be left unchanged", 1, sorts.length);
    Assert.assertEquals(
        "Original sort field should remain",
        Field.ENTRY_CLASS_PK,
        sorts[0].getFieldName());
    Assert.assertEquals("Original sort type should remain", Sort.LONG_TYPE, sorts[0].getType());
    Assert.assertFalse("Original sort direction should remain", sorts[0].isReverse());
  }

  @Test
  public void shouldEnableScoringButPreserveNonDefaultSort() {
    var searchContext = createSearchContext("some keywords");
    var customSorts = new Sort[] {new Sort("modified_sortable", Sort.LONG_TYPE, true)};
    searchContext.setSorts(customSorts);

    contributor.contribute(searchContext, null);

    Assert.assertTrue(
        "Score should be enabled even when sort is not replaced",
        searchContext.getQueryConfig().isScoreEnabled());

    var sorts = searchContext.getSorts();
    Assert.assertEquals("Custom sort should be preserved", 1, sorts.length);
    Assert.assertEquals(
        "Custom sort field name should remain",
        "modified_sortable",
        sorts[0].getFieldName());
    Assert.assertTrue("Custom sort reverse flag should remain", sorts[0].isReverse());
  }

  @Test
  public void shouldNotReplaceSortWhenMultipleSortsArePresent() {
    var searchContext = createSearchContext("some keywords");
    var multipleSorts =
        new Sort[] {
          new Sort(Field.ENTRY_CLASS_PK, Sort.LONG_TYPE, false),
          new Sort("modified_sortable", Sort.LONG_TYPE, true)
        };
    searchContext.setSorts(multipleSorts);

    contributor.contribute(searchContext, null);

    Assert.assertTrue(
        "Score should be enabled in query config",
        searchContext.getQueryConfig().isScoreEnabled());

    var sorts = searchContext.getSorts();
    Assert.assertEquals(
        "Multiple sorts should not be replaced with a single score sort", 2, sorts.length);
  }

  private static SearchContext createSearchContext(String keywords) {
    var searchContext = new SearchContext();
    try {
      searchContext.setCompanyId(TestPropsValues.getCompanyId());
    } catch (Exception e) {
      throw new RuntimeException("Failed to set company id on SearchContext", e);
    }
    searchContext.setLocale(LocaleUtil.US);
    if (keywords != null) {
      searchContext.setKeywords(keywords);
    }
    return searchContext;
  }

  private static Sort[] defaultVulcanSort() {
    return new Sort[] {new Sort(Field.ENTRY_CLASS_PK, Sort.LONG_TYPE, false)};
  }
}
