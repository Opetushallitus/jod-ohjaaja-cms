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
import com.liferay.journal.model.JournalArticle;
import com.liferay.portal.kernel.search.BooleanClause;
import com.liferay.portal.kernel.search.BooleanClauseOccur;
import com.liferay.portal.kernel.search.BooleanQuery;
import com.liferay.portal.kernel.search.Field;
import com.liferay.portal.kernel.search.Query;
import com.liferay.portal.kernel.search.SearchContext;
import com.liferay.portal.kernel.search.generic.BooleanQueryImpl;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.PermissionCheckerFactory;
import com.liferay.portal.kernel.security.permission.PermissionThreadLocal;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.search.spi.model.query.contributor.KeywordQueryContributor;
import com.liferay.portal.search.spi.model.query.contributor.helper.KeywordQueryContributorHelper;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import fi.okm.jod.ohjaaja.cms.search.JodJournalArticleKeywordQueryContributor;
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
 * Integration tests for JodJournalArticleKeywordQueryContributor.
 *
 * Verifies the OSGi service registration as well as the contribute() behavior
 * - boosted title term queries are added for non-blank keywords
 * - keywords are taken from the SearchContext when none are passed in
 * - the contributor exits early when no keywords are available
 */
@RunWith(JodInContainerRunner.class)
public class JodJournalArticleKeywordQueryContributorTest {

  @ClassRule @Rule
  public static final LiferayIntegrationTestRule liferayIntegrationTestRule =
      new LiferayIntegrationTestRule();

  private static final float TITLE_BOOST = 5.0f;
  private static final String JOURNAL_ARTICLE_CLASS_NAME =
      "com.liferay.journal.model.JournalArticle";

  private static BundleContext bundleContext;
  private static PermissionChecker originalPermissionChecker;
  private static JodJournalArticleKeywordQueryContributor contributor;
  private static ServiceReference<?> contributorReference;

  @BeforeClass
  public static void setUpClass() throws Exception {
    var bundle = FrameworkUtil.getBundle(JodJournalArticleKeywordQueryContributorTest.class);
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
        "JodJournalArticleKeywordQueryContributor must be registered for JournalArticle",
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

  private static JodJournalArticleKeywordQueryContributor lookupContributor() throws Exception {
    var references =
        bundleContext.getAllServiceReferences(
            KeywordQueryContributor.class.getName(),
            "(indexer.class.name=" + JOURNAL_ARTICLE_CLASS_NAME + ")");
    if (references == null) {
      return null;
    }

    for (ServiceReference<?> ref : references) {
      var service = bundleContext.getService(ref);
      if (service instanceof JodJournalArticleKeywordQueryContributor jodContributor) {
        contributorReference = ref;
        return jodContributor;
      }
      bundleContext.ungetService(ref);
    }
    return null;
  }

  @Test
  public void shouldHaveKeywordQueryContributorRegistered() throws Exception {
    var references =
        bundleContext.getAllServiceReferences(
            KeywordQueryContributor.class.getName(),
            "(indexer.class.name=" + JOURNAL_ARTICLE_CLASS_NAME + ")");

    Assert.assertNotNull("Should have KeywordQueryContributor services", references);
    Assert.assertTrue(
        "Should have at least one KeywordQueryContributor for JournalArticle",
        references.length > 0);

    var found = false;
    for (ServiceReference<?> ref : references) {
      var service = bundleContext.getService(ref);
      try {
        if (service instanceof JodJournalArticleKeywordQueryContributor) {
          found = true;

          var indexerClassName = ref.getProperty("indexer.class.name");
          Assert.assertEquals(
              "Should be configured for JournalArticle",
              JOURNAL_ARTICLE_CLASS_NAME,
              indexerClassName);

          var ranking = ref.getProperty("service.ranking");
          Assert.assertEquals(
              "Service ranking should be 100 to override default contributors",
              100,
              ranking);
          break;
        }
      } finally {
        bundleContext.ungetService(ref);
      }
    }

    Assert.assertTrue(
        "JodJournalArticleKeywordQueryContributor should be registered", found);
  }

  @Test
  public void shouldAddBoostedTitleClauseForKeywords() {
    var searchContext = createSearchContext("Test Keyword");
    var booleanQuery = new BooleanQueryImpl();

    contributor.contribute("Test Keyword", booleanQuery, helperFor(searchContext));

    var clauses = booleanQuery.clauses();
    Assert.assertFalse(
        "BooleanQuery should contain SHOULD clause with boosted title term queries",
        clauses.isEmpty());

    var boostedClause = clauses.getFirst();
    Assert.assertEquals(
        "Boosted query should be added as a SHOULD clause",
        BooleanClauseOccur.SHOULD,
        boostedClause.getBooleanClauseOccur());

    var boostedQuery = boostedClause.getClause();
    Assert.assertTrue(
        "Boosted clause should wrap a BooleanQuery of title term queries",
        boostedQuery instanceof BooleanQuery);

    var titleClauses = ((BooleanQuery) boostedQuery).clauses();
    Assert.assertFalse(
        "There should be at least one localized title term query", titleClauses.isEmpty());

    var allBoosted = true;
    var allTitleFields = true;
    for (BooleanClause<?> titleClause : titleClauses) {
      var termQuery = titleClause.getClause();
      if (!(termQuery instanceof Query query)) {
        Assert.fail("Each clause should wrap a Query but was " + termQuery);
        return;
      }
      if (Math.abs(query.getBoost() - TITLE_BOOST) > 0.0001f) {
        allBoosted = false;
      }
      var queryString = query.toString();
      // Localized title fields are named like title_en_US, title_fi_FI etc.
      if (!queryString.contains(Field.TITLE)) {
        allTitleFields = false;
      }
    }

    Assert.assertTrue(
        "All term queries should have the title boost factor " + TITLE_BOOST, allBoosted);
    Assert.assertTrue(
        "All term queries should be on a localized title field", allTitleFields);
  }

  @Test
  public void shouldFallBackToSearchContextKeywords() {
    var searchContext = createSearchContext("fallback keyword");
    var booleanQuery = new BooleanQueryImpl();

    // Pass blank keywords - the contributor should pick them up from the SearchContext instead.
    contributor.contribute("", booleanQuery, helperFor(searchContext));

    Assert.assertFalse(
        "BooleanQuery should still receive clauses when keywords are read from SearchContext",
        booleanQuery.clauses().isEmpty());
  }

  @Test
  public void shouldNotAddClausesWhenKeywordsAreBlank() {
    var searchContext = createSearchContext(null);
    var booleanQuery = new BooleanQueryImpl();

    contributor.contribute(null, booleanQuery, helperFor(searchContext));

    Assert.assertTrue(
        "BooleanQuery should remain empty when no keywords are available",
        booleanQuery.clauses().isEmpty());
  }

  @Test
  public void shouldNotAddClausesWhenKeywordsAreEmpty() {
    var searchContext = createSearchContext("");
    var booleanQuery = new BooleanQueryImpl();

    contributor.contribute("", booleanQuery, helperFor(searchContext));

    Assert.assertTrue(
        "BooleanQuery should remain empty when keywords are empty in both args and context",
        booleanQuery.clauses().isEmpty());
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

  private static KeywordQueryContributorHelper helperFor(SearchContext searchContext) {
    return new KeywordQueryContributorHelper() {
      @Override
      public String getClassName() {
        return JournalArticle.class.getName();
      }

      @Override
      public String[] getSearchClassNames() {
        return new String[] {JournalArticle.class.getName()};
      }

      @Override
      public SearchContext getSearchContext() {
        return searchContext;
      }
    };
  }
}
