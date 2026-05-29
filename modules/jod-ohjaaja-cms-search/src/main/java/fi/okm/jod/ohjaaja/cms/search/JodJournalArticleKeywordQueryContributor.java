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
import com.liferay.portal.kernel.search.BooleanClauseOccur;
import com.liferay.portal.kernel.search.BooleanQuery;
import com.liferay.portal.kernel.search.Field;
import com.liferay.portal.kernel.search.ParseException;
import com.liferay.portal.kernel.search.Query;
import com.liferay.portal.kernel.search.SearchContext;
import com.liferay.portal.kernel.search.generic.BooleanQueryImpl;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.search.localization.SearchLocalizationHelper;
import com.liferay.portal.search.spi.model.query.contributor.KeywordQueryContributor;
import com.liferay.portal.search.spi.model.query.contributor.helper.KeywordQueryContributorHelper;
import java.util.Locale;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Keyword query contributor for JournalArticle search enhancement.
 *
 * <p>This contributor improves search results for Journal Articles by adding weighted term queries
 * on localized title fields. Title field matches receive a boost factor of 5.0 to prioritize
 * article titles over other searchable fields.
 *
 * <p>The contributor: - Validates and processes search keywords from the search context - Applies
 * locale-specific field localization for multi-language support - Creates boosted SHOULD clauses to
 * ensure flexible keyword matching - Handles parse exceptions gracefully with logging
 *
 * <p>Service ranking is set to 100 to ensure this implementation takes precedence over default
 * keyword query contributors for JournalArticle indexing.
 *
 * @see KeywordQueryContributor
 */
@Component(
    property = {
      "indexer.class.name=com.liferay.journal.model.JournalArticle",
      "service.ranking:Integer=100"
    },
    service = KeywordQueryContributor.class)
public class JodJournalArticleKeywordQueryContributor implements KeywordQueryContributor {
  /** Boost factor applied to title field matches to prioritize title relevance. */
  private static final float TITLE_BOOST = 5.0f;

  @Reference private SearchLocalizationHelper searchLocalizationHelper;

  private static final Log log =
      LogFactoryUtil.getLog(JodJournalArticleKeywordQueryContributor.class);

  /**
   * Contributes enhanced keyword query clauses for JournalArticle search.
   *
   * <p>Adds boosted term queries on localized title fields to the boolean query. If no keywords are
   * provided in the parameters, retrieves them from the search context. Returns early if no
   * keywords are available.
   *
   * @param keywords the search keywords to query for; if blank, uses search context keywords
   * @param booleanQuery the target boolean query to add clauses to
   * @param keywordQueryContributorHelper helper containing search context and utilities
   */
  @Override
  public void contribute(
      String keywords,
      BooleanQuery booleanQuery,
      KeywordQueryContributorHelper keywordQueryContributorHelper) {

    SearchContext searchContext = keywordQueryContributorHelper.getSearchContext();

    // Use provided keywords or fall back to keywords from search context
    if (Validator.isBlank(keywords)) {
      keywords = searchContext.getKeywords();
    }
    // Exit early if no keywords are available
    if (Validator.isBlank(keywords)) {
      return;
    }

    // Retrieve localized title field names based on search context language
    String[] localizedTitleFields =
        searchLocalizationHelper.getLocalizedFieldNames(new String[] {Field.TITLE}, searchContext);

    // Create boosted query with SHOULD clauses for flexible matching
    BooleanQuery boostedQuery = new BooleanQueryImpl();
    Locale locale = LocaleUtil.fromLanguageId(searchContext.getLanguageId(), true, true);
    String value = StringUtil.toLowerCase(keywords, locale);

    // Add term queries for each localized title field with boost factor
    for (String fieldName : localizedTitleFields) {
      try {
        Query termQuery = boostedQuery.addTerm(fieldName, value, false, BooleanClauseOccur.SHOULD);
        termQuery.setBoost(TITLE_BOOST);
      } catch (ParseException parseException) {
        log.error(parseException.getMessage(), parseException);
      }
    }

    // Add the boosted query to the main boolean query as a SHOULD clause
    try {
      booleanQuery.add(boostedQuery, BooleanClauseOccur.SHOULD);
    } catch (ParseException parseException) {
      log.error(parseException.getMessage(), parseException);
    }
  }
}
