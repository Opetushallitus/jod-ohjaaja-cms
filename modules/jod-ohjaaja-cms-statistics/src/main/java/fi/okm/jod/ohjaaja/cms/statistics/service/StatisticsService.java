/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.statistics.service;

import static fi.okm.jod.ohjaaja.cms.statistics.util.TokenUtil.getToken;

import com.liferay.asset.kernel.service.AssetCategoryLocalService;
import com.liferay.journal.service.JournalArticleLocalService;
import com.liferay.portal.kernel.language.LanguageUtil;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.PropsUtil;
import fi.okm.jod.ohjaaja.cms.statistics.client.StatisticsApiClient;
import fi.okm.jod.ohjaaja.cms.statistics.client.exception.StatisticsApiException;
import fi.okm.jod.ohjaaja.cms.statistics.dto.ArticleCountDto;
import fi.okm.jod.ohjaaja.cms.statistics.dto.InterestCountDto;
import fi.okm.jod.ohjaaja.cms.statistics.dto.WorkplaceCountDto;
import fi.okm.jod.ohjaaja.cms.statistics.view.ArticleRow;
import fi.okm.jod.ohjaaja.cms.statistics.view.DistributionRow;
import fi.okm.jod.ohjaaja.cms.statistics.view.StatisticsView;
import fi.okm.jod.ohjaaja.cms.util.JodOhjaajaCmsUtil;
import jakarta.portlet.PortletRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = StatisticsService.class)
public class StatisticsService {
  private static final Log log = LogFactoryUtil.getLog(StatisticsService.class);

  private static final String OHJAAJA_ARTICLE_URL_PREFIX =
      PropsUtil.get("ohjaaja.frontend.url") + "/fi/artikkeli/";

  @Reference private StatisticsApiClient statisticsApiClient;
  @Reference private AssetCategoryLocalService assetCategoryLocalService;
  @Reference private JournalArticleLocalService journalArticleLocalService;
  @Reference private JodOhjaajaCmsUtil jodOhjaajaCmsUtil;

  public StatisticsView getStatistics(
      PortletRequest portletRequest,
      ResourceBundle resourceBundle,
      Locale locale,
      LocalDate alku,
      LocalDate loppu,
      int top)
      throws StatisticsApiException {
    var dto = statisticsApiClient.fetchStatistics(getToken(portletRequest), alku, loppu, top);
    var groupId = jodOhjaajaCmsUtil.getJodOhjaajaCmsGroup().getGroupId();

    return new StatisticsView(
        dto.alku(),
        dto.loppu(),
        dto.kayttajienMaara(),
        toDistribution(
            dto.tyoskentelyPaikat(),
            w ->
                LanguageUtil.get(
                    resourceBundle,
                    w.tyoskentelyPaikka() == null
                        ? "statistics.workplace.none"
                        : "statistics.workplace." + w.tyoskentelyPaikka()),
            WorkplaceCountDto::maara),
        toDistribution(
            dto.kiinnostukset(),
            k -> getCategoryTitle(k.asiasanaId(), locale),
            InterestCountDto::maara),
        toArticleRows(dto.suosituimmatArtikkelit(), groupId, locale),
        toArticleRows(dto.kommentoiduimmatArtikkelit(), groupId, locale),
        toArticleRows(dto.katsotuimmatArtikkelit(), groupId, locale));
  }

  private static <T> List<DistributionRow> toDistribution(
      List<T> items, Function<T, String> label, ToLongFunction<T> count) {
    if (items == null) {
      return List.of();
    }
    var total = items.stream().mapToLong(count).sum();
    return items.stream()
        .map(
            item -> {
              var c = count.applyAsLong(item);
              var percent = total == 0 ? 0 : (int) Math.round(100.0 * c / total);
              return new DistributionRow(label.apply(item), c, percent);
            })
        .toList();
  }

  private List<ArticleRow> toArticleRows(
      List<ArticleCountDto> articles, long groupId, Locale locale) {
    if (articles == null) {
      return List.of();
    }
    return articles.stream()
        .map(
            a ->
                new ArticleRow(
                    getArticleTitle(groupId, a.artikkeliErc(), locale),
                    OHJAAJA_ARTICLE_URL_PREFIX + a.artikkeliErc(),
                    a.maara()))
        .toList();
  }

  private String getCategoryTitle(Long categoryId, Locale locale) {
    var fallback = "#" + categoryId;
    if (categoryId == null) {
      return fallback;
    }
    try {
      var category = assetCategoryLocalService.fetchAssetCategory(categoryId);
      return category != null ? category.getTitle(locale) : fallback;
    } catch (Exception e) {
      log.warn("Could not resolve category " + categoryId, e);
      return fallback;
    }
  }

  private String getArticleTitle(long groupId, String externalReferenceCode, Locale locale) {
    try {
      var article =
          journalArticleLocalService.fetchLatestArticleByExternalReferenceCode(
              groupId, externalReferenceCode);
      return article != null ? article.getTitle(locale) : externalReferenceCode;
    } catch (Exception e) {
      log.warn("Could not resolve article " + externalReferenceCode, e);
      return externalReferenceCode;
    }
  }
}
