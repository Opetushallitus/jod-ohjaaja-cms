/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.search;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.search.Field;
import com.liferay.portal.kernel.search.QueryConfig;
import com.liferay.portal.kernel.search.SearchContext;
import com.liferay.portal.kernel.search.Sort;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.search.legacy.searcher.SearchRequestBuilderFactory;
import com.liferay.portal.search.searcher.SearchRequestBuilder;
import com.liferay.portal.search.sort.ScoreSort;
import com.liferay.portal.search.sort.SortOrder;
import com.liferay.portal.search.sort.Sorts;
import com.liferay.portal.search.spi.model.query.contributor.SearchContextContributor;
import com.liferay.portal.search.spi.model.query.contributor.helper.SearchContextContributorHelper;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Search context contributor for JournalArticle indexing and query building.
 *
 * <p>This contributor enhances search context configuration for Journal Articles by: - Enabling
 * relevance scoring when keywords are present - Automatically applying score-based sorting when
 * default Vulcan sorting is detected - Converting default sorting to descending score-based sorting
 * for better relevance
 *
 * <p>The contributor detects when the search uses Vulcan's default sorting by entry class PK and
 * replaces it with score-based sorting to ensure results are ordered by search relevance. This
 * improves search result presentation by prioritizing the most relevant matches.
 *
 * @see SearchContextContributor
 */
@Component(
    property = "indexer.class.name=com.liferay.journal.model.JournalArticle",
    service = SearchContextContributor.class)
public class JodJournalArticleSearchContextContributor implements SearchContextContributor {

  private static final Log log =
      LogFactoryUtil.getLog(JodJournalArticleSearchContextContributor.class);

  @Reference private SearchRequestBuilderFactory searchRequestBuilderFactory;

  @Reference private Sorts sorts;

  /**
   * Contributes enhanced search context configuration for JournalArticle queries.
   *
   * <p>Enables relevance scoring and applies score-based sorting when applicable: - Requires
   * keywords to be present in the search context - Enables query scoring in the query config -
   * Detects and replaces default Vulcan entry class PK sorting with relevance scoring - Configures
   * descending score sort order for optimal result ranking
   *
   * @param searchContext the search context to be enhanced
   * @param searchContextContributorHelper helper providing search context utilities
   */
  @Override
  public void contribute(
      SearchContext searchContext, SearchContextContributorHelper searchContextContributorHelper) {
    // Exit early if no keywords are present in the search
    if (Validator.isBlank(searchContext.getKeywords())) {
      return;
    }

    // Enable relevance scoring in query configuration
    QueryConfig queryConfig = searchContext.getQueryConfig();
    queryConfig.setScoreEnabled(true);

    // Check if current sorting is Vulcan's default sorting
    if (!isDefaultVulcanSort(searchContext.getSorts())) {
      return;
    }

    // Replace default sorting with score-based sorting
    searchContext.setSorts(new Sort(null, Sort.SCORE_TYPE, false));

    // Configure score sort with descending order for best relevance first
    ScoreSort scoreSort = sorts.score();
    scoreSort.setSortOrder(SortOrder.DESC);

    // Build and apply the search request with score-based sorting
    SearchRequestBuilder searchRequestBuilder = searchRequestBuilderFactory.builder(searchContext);
    searchRequestBuilder.sorts(scoreSort);

    log.info("Forced relevance sort for keyword search: " + searchContext.getKeywords());
  }

  /**
   * Detects whether the given sorts array represents Vulcan's default sorting.
   *
   * <p>Default Vulcan sorting consists of a single sort on the ENTRY_CLASS_PK field with LONG_TYPE
   * and non-reversed (ascending) order.
   *
   * @param currentSorts the sort array to validate
   * @return true if the sorts represent default Vulcan sorting, false otherwise
   */
  private boolean isDefaultVulcanSort(Sort[] currentSorts) {
    // Validate that exactly one sort is present
    if (ArrayUtil.isEmpty(currentSorts) || (currentSorts.length != 1)) {
      return false;
    }

    // Check if the single sort matches Vulcan default sorting criteria
    Sort sort = currentSorts[0];
    return Field.ENTRY_CLASS_PK.equals(sort.getFieldName())
        && (sort.getType() == Sort.LONG_TYPE)
        && !sort.isReverse();
  }
}
