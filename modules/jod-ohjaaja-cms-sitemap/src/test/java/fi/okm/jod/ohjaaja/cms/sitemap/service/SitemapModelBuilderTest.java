/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.sitemap.service;

import fi.okm.jod.ohjaaja.cms.navigation.dto.NavigationItemDto;
import fi.okm.jod.ohjaaja.cms.sitemap.model.SitemapUrl;
import fi.okm.jod.ohjaaja.cms.util.SlugUtil;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Assert;
import org.junit.Test;

/** Unit tests for the sitemap rules: what is published, URLs, alternates and lastmod. */
public class SitemapModelBuilderTest {

  private static final String FRONTEND_URL = "https://osaamispolku.fi/ohjaaja";
  private static final String FI = "fi-FI";
  private static final String SV = "sv-SE";
  private static final String EN = "en-US";

  private final AtomicLong ids = new AtomicLong();
  private final Map<Long, Date> modifiedDates = new HashMap<>();

  @Test
  public void testFrontPagesAreListedInEveryLanguage() {
    var urls = build(List.of());

    Assert.assertEquals(
        List.of(FRONTEND_URL + "/fi", FRONTEND_URL + "/sv", FRONTEND_URL + "/en"), locs(urls));
    var frontPage = urls.getFirst();
    Assert.assertNull(frontPage.lastmod());
    Assert.assertEquals(
        Map.of(
            "fi", FRONTEND_URL + "/fi",
            "sv", FRONTEND_URL + "/sv",
            "en", FRONTEND_URL + "/en",
            "x-default", FRONTEND_URL + "/fi"),
        frontPage.alternates());
  }

  @Test
  public void testPathIsBuiltFromTheSlugsOfTheAncestors() {
    var article =
        article("erc-1", Map.of(FI, "Artikkelin nimi", SV, "Artikelns namn"), "2026-09-30");
    var category =
        category(
            "CategoryMain",
            Map.of(FI, "Ohjauksen työkalut", SV, "Verktyg för vägledning"),
            article);

    var urls = build(List.of(category));

    var url = find(urls, FRONTEND_URL + "/fi/ohjauksen-tyokalut/artikkelin-nimi");
    Assert.assertEquals(LocalDate.parse("2026-09-30"), url.lastmod());
    Assert.assertEquals(
        Map.of(
            "fi", FRONTEND_URL + "/fi/ohjauksen-tyokalut/artikkelin-nimi",
            "sv", FRONTEND_URL + "/sv/verktyg-for-vagledning/artikelns-namn",
            "x-default", FRONTEND_URL + "/fi/ohjauksen-tyokalut/artikkelin-nimi"),
        url.alternates());
    Assert.assertTrue(locs(urls).contains(FRONTEND_URL + "/sv/verktyg-for-vagledning"));
  }

  @Test
  public void testItemIsListedBeforeItsChildren() {
    var article = article("erc-1", Map.of(FI, "Artikkeli"), "2026-09-30");
    var category = category("CategoryListing", Map.of(FI, "Kategoria"), article);

    var locs = locs(build(List.of(category)));

    Assert.assertEquals(
        List.of(
            FRONTEND_URL + "/fi",
            FRONTEND_URL + "/sv",
            FRONTEND_URL + "/en",
            FRONTEND_URL + "/fi/kategoria",
            FRONTEND_URL + "/fi/kategoria/artikkeli"),
        locs);
  }

  @Test
  public void testUntranslatedArticleIsNotListedInThatLanguage() {
    var article = article("erc-1", Map.of(FI, "Artikkeli", EN, "Article"), "2026-09-30");
    var category =
        category("CategoryMain", Map.of(FI, "Kategoria", SV, "Kategori", EN, "Category"), article);

    var locs = locs(build(List.of(category)));

    Assert.assertTrue(locs.contains(FRONTEND_URL + "/fi/kategoria/artikkeli"));
    Assert.assertTrue(locs.contains(FRONTEND_URL + "/en/category/article"));
    Assert.assertFalse(locs.contains(FRONTEND_URL + "/sv/kategori/artikel"));
    // The category itself has a Swedish name and is listed
    Assert.assertTrue(locs.contains(FRONTEND_URL + "/sv/kategori"));
  }

  @Test
  public void testCategoryWithoutNameHidesItsSubtreeInThatLanguage() {
    var article = article("erc-1", Map.of(FI, "Artikkeli", SV, "Artikel"), "2026-09-30");
    var category = category("CategoryMain", Map.of(FI, "Kategoria"), article);

    var urls = build(List.of(category));

    Assert.assertTrue(locs(urls).stream().noneMatch(loc -> loc.startsWith(FRONTEND_URL + "/sv/")));
    // Without a Finnish version there is no x-default
    var url = find(urls, FRONTEND_URL + "/fi/kategoria/artikkeli");
    Assert.assertEquals(Set.of("fi", "x-default"), url.alternates().keySet());
  }

  @Test
  public void testUnresolvedItemsAreSkippedWithTheirSubtrees() {
    var article = article("erc-1", Map.of(FI, "Artikkeli"), "2026-09-30");
    var hiddenCategory =
        item("CategoryMain", "cat-hidden", Map.of(FI, "Piilotettu"), null, null, List.of(article));
    var hiddenArticle = item("Article", "erc-2", Map.of(), null, null, List.of());
    var urlItem = item("url", "url-1", Map.of(FI, "Linkki"), null, null, List.of());
    var draftArticle = item("Article", "erc-3", Map.of(FI, "Luonnos"), 999L, null, List.of());

    var urls = build(List.of(hiddenCategory, hiddenArticle, urlItem, draftArticle));

    // Only the front pages
    Assert.assertEquals(3, urls.size());
  }

  @Test
  public void testEarlierSiblingWithTheSamePathWins() {
    var first = article("erc-1", Map.of(FI, "Sama nimi"), "2026-09-01");
    var duplicate = article("erc-2", Map.of(FI, "Sama  nimi!"), "2026-09-02");

    var urls = build(List.of(first, duplicate));

    Assert.assertEquals(1, locs(urls).stream().filter(loc -> loc.contains("/sama-nimi")).count());
    Assert.assertEquals(
        LocalDate.parse("2026-09-01"), find(urls, FRONTEND_URL + "/fi/sama-nimi").lastmod());
  }

  @Test
  public void testCategoryWithChildrenWinsOverAnEarlierLeafWithTheSamePath() {
    // React Router ranks the category's index route above the article's route
    var leaf = article("erc-1", Map.of(FI, "Sama nimi"), "2026-09-01");
    var category =
        category(
            "CategoryListing",
            Map.of(FI, "Sama nimi"),
            article("erc-2", Map.of(FI, "Lapsi"), "2026-09-02"));

    var urls = build(List.of(leaf, category));

    // The category's lastmod, not the leaf's
    Assert.assertEquals(
        LocalDate.parse("2026-09-02"), find(urls, FRONTEND_URL + "/fi/sama-nimi").lastmod());
    Assert.assertTrue(locs(urls).contains(FRONTEND_URL + "/fi/sama-nimi/lapsi"));
  }

  @Test
  public void testItemLeftOutOfTheSitemapCanStillTakeThePath() {
    // The UI routes a broken item too, with the name stored in the menu item
    var broken = brokenItem("Sama nimi");
    var article = article("erc-1", Map.of(FI, "Sama nimi"), "2026-09-01");

    var urls = build(List.of(broken, article));

    Assert.assertFalse(locs(urls).contains(FRONTEND_URL + "/fi/sama-nimi"));
  }

  @Test
  public void testItemOfAnotherLanguageCanTakeThePath() {
    // Without a Swedish name the UI routes the Finnish article at /sv/sama-nimi too, before the
    // Swedish one, as the languages share the /:lng route
    var finnish = article("erc-1", Map.of(FI, "Sama nimi"), "2026-09-01");
    var swedish = article("erc-2", Map.of(SV, "Sama nimi"), "2026-09-02");

    var urls = build(List.of(finnish, swedish));

    Assert.assertTrue(locs(urls).contains(FRONTEND_URL + "/fi/sama-nimi"));
    Assert.assertFalse(locs(urls).contains(FRONTEND_URL + "/sv/sama-nimi"));
  }

  @Test
  public void testCategoryLastmodIsTheNewestArticleOfItsSubtree() {
    var deepArticle = article("erc-1", Map.of(FI, "Syvä"), "2026-09-15");
    var subcategory = category("CategoryListing", Map.of(FI, "Ala"), deepArticle);
    var article = article("erc-2", Map.of(FI, "Pinta"), "2026-08-01");
    var untranslated = article("erc-3", Map.of(SV, "Nyaste"), "2026-10-01");
    var category = category("CategoryMain", Map.of(FI, "Pää"), subcategory, article, untranslated);
    var emptyCategory = category("CategoryMain", Map.of(FI, "Tyhjä"));

    var urls = build(List.of(category, emptyCategory));

    Assert.assertEquals(
        LocalDate.parse("2026-09-15"), find(urls, FRONTEND_URL + "/fi/paa").lastmod());
    Assert.assertEquals(
        LocalDate.parse("2026-09-15"), find(urls, FRONTEND_URL + "/fi/paa/ala").lastmod());
    Assert.assertNull(find(urls, FRONTEND_URL + "/fi/tyhja").lastmod());
  }

  @Test
  public void testLastmodIsInHelsinkiTime() {
    var id = ids.incrementAndGet();
    modifiedDates.put(id, Date.from(Instant.parse("2026-09-30T22:30:00Z")));
    var article = item("Article", "erc-1", Map.of(FI, "Artikkeli"), id, null, List.of());

    var urls = build(List.of(article));

    Assert.assertEquals(
        LocalDate.parse("2026-10-01"), find(urls, FRONTEND_URL + "/fi/artikkeli").lastmod());
  }

  @Test
  public void testOnlyTheCanonicalPlacementOfAnArticleIsListed() {
    var article = article("erc-1", Map.of(FI, "Artikkeli", EN, "Article"), "2026-09-30");
    var category = category("CategoryMain", Map.of(FI, "Kategoria", EN, "Category"), article);
    // The same article again under another category: the first placement is canonical
    var sameArticle =
        item("Article", "erc-1", article.name_i18n(), article.articleId(), null, List.of());
    var otherCategory = category("CategoryMain", Map.of(FI, "Toinen"), sameArticle);

    var urls = build(List.of(category, otherCategory));

    Assert.assertEquals(
        Map.of(
            "fi", FRONTEND_URL + "/fi/kategoria/artikkeli",
            "en", FRONTEND_URL + "/en/category/article",
            "x-default", FRONTEND_URL + "/fi/kategoria/artikkeli"),
        find(urls, FRONTEND_URL + "/fi/kategoria/artikkeli").alternates());
    Assert.assertFalse(locs(urls).contains(FRONTEND_URL + "/fi/toinen/artikkeli"));
    Assert.assertTrue(locs(urls).contains(FRONTEND_URL + "/fi/toinen"));
  }

  @Test
  public void testChildrenOfAnItemLeftOutAreStillListed() {
    var first =
        category(
            "CategoryMain",
            Map.of(FI, "Sama nimi"),
            article("erc-1", Map.of(FI, "Eka"), "2026-09-01"));
    var second =
        category(
            "CategoryMain",
            Map.of(FI, "Sama nimi"),
            article("erc-2", Map.of(FI, "Toka"), "2026-09-02"));

    var urls = build(List.of(first, second));

    Assert.assertEquals(
        1, locs(urls).stream().filter(loc -> loc.endsWith("/fi/sama-nimi")).count());
    Assert.assertTrue(locs(urls).contains(FRONTEND_URL + "/fi/sama-nimi/toka"));
  }

  private List<SitemapUrl> build(List<NavigationItemDto> items) {
    return new SitemapModelBuilder(FRONTEND_URL, modifiedDates).build(items);
  }

  private NavigationItemDto article(String erc, Map<String, String> names, String modifiedDate) {
    var articleId = ids.incrementAndGet();
    modifiedDates.put(articleId, Date.from(Instant.parse(modifiedDate + "T09:00:00Z")));
    return item("Article", erc, names, articleId, null, List.of());
  }

  /** An item whose target is missing or hidden: no ID or names, only the menu item's title. */
  private NavigationItemDto brokenItem(String title) {
    return new NavigationItemDto(
        ids.incrementAndGet(),
        title,
        Map.of(),
        SlugUtil.sluggify(title),
        Map.of(),
        "",
        Map.of(),
        "Article",
        false,
        false,
        false,
        false,
        "broken-" + ids.get(),
        null,
        null,
        List.of(),
        0L);
  }

  private NavigationItemDto category(
      String type, Map<String, String> names, NavigationItemDto... children) {
    return item(
        type,
        "cat-" + ids.incrementAndGet(),
        names,
        null,
        ids.incrementAndGet(),
        List.of(children));
  }

  private NavigationItemDto item(
      String type,
      String erc,
      Map<String, String> names,
      Long articleId,
      Long categoryId,
      List<NavigationItemDto> children) {
    return new NavigationItemDto(
        ids.incrementAndGet(),
        names.getOrDefault(FI, "fallback"),
        names,
        SlugUtil.sluggify(names.getOrDefault(FI, "fallback")),
        SlugUtil.sluggifyAll(names),
        "",
        Map.of(),
        type,
        false,
        false,
        false,
        false,
        erc,
        articleId,
        categoryId,
        children,
        0L);
  }

  private static List<String> locs(List<SitemapUrl> urls) {
    return urls.stream().map(SitemapUrl::loc).toList();
  }

  private static SitemapUrl find(List<SitemapUrl> urls, String loc) {
    return urls.stream()
        .filter(url -> url.loc().equals(loc))
        .findFirst()
        .orElseThrow(() -> new AssertionError("URL not in sitemap: " + loc));
  }
}
