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
import fi.okm.jod.ohjaaja.cms.sitemap.model.Breadcrumb;
import fi.okm.jod.ohjaaja.cms.sitemap.model.Language;
import fi.okm.jod.ohjaaja.cms.sitemap.model.SitemapModel;
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
  private static final String CONTENT_URL = FRONTEND_URL + "/cms/o/jod-sitemap/content";

  private final AtomicLong ids = new AtomicLong();
  private final Map<Long, Date> modifiedDates = new HashMap<>();

  @Test
  public void testFrontPagesAreListedInEveryLanguage() {
    var model = build(List.of());

    Assert.assertEquals(
        List.of(FRONTEND_URL + "/fi", FRONTEND_URL + "/sv", FRONTEND_URL + "/en"), locs(model));
    var frontPage = model.urls().getFirst();
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

    var model = build(List.of(category));

    var url = find(model, FRONTEND_URL + "/fi/ohjauksen-tyokalut/artikkelin-nimi");
    Assert.assertEquals(LocalDate.parse("2026-09-30"), url.lastmod());
    Assert.assertEquals(
        Map.of(
            "fi", FRONTEND_URL + "/fi/ohjauksen-tyokalut/artikkelin-nimi",
            "sv", FRONTEND_URL + "/sv/verktyg-for-vagledning/artikelns-namn",
            "x-default", FRONTEND_URL + "/fi/ohjauksen-tyokalut/artikkelin-nimi"),
        url.alternates());
    Assert.assertTrue(locs(model).contains(FRONTEND_URL + "/sv/verktyg-for-vagledning"));
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

    var model = build(List.of(category));

    Assert.assertTrue(locs(model).stream().noneMatch(loc -> loc.startsWith(FRONTEND_URL + "/sv/")));
    Assert.assertTrue(model.findArticlePage(Language.SV, "erc-1").isEmpty());
    // Without a Finnish version there is no x-default
    var url = find(model, FRONTEND_URL + "/fi/kategoria/artikkeli");
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

    var model = build(List.of(hiddenCategory, hiddenArticle, urlItem, draftArticle));

    Assert.assertEquals(3, model.urls().size());
    Assert.assertTrue(model.articlePages().isEmpty());
  }

  @Test
  public void testEarlierSiblingWithTheSamePathWins() {
    var first = article("erc-1", Map.of(FI, "Sama nimi"), "2026-09-01");
    var duplicate = article("erc-2", Map.of(FI, "Sama  nimi!"), "2026-09-02");

    var model = build(List.of(first, duplicate));

    Assert.assertEquals(1, locs(model).stream().filter(loc -> loc.contains("/sama-nimi")).count());
    Assert.assertTrue(model.findArticlePage(Language.FI, "erc-1").isPresent());
    Assert.assertTrue(model.findArticlePage(Language.FI, "erc-2").isEmpty());
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

    var model = build(List.of(leaf, category));

    Assert.assertTrue(model.findArticlePage(Language.FI, "erc-1").isEmpty());
    Assert.assertEquals(
        LocalDate.parse("2026-09-02"), find(model, FRONTEND_URL + "/fi/sama-nimi").lastmod());
    Assert.assertTrue(locs(model).contains(FRONTEND_URL + "/fi/sama-nimi/lapsi"));
  }

  @Test
  public void testItemLeftOutOfTheSitemapCanStillTakeThePath() {
    // The UI routes a broken item too, with the name stored in the menu item
    var broken = brokenItem("Sama nimi");
    var article = article("erc-1", Map.of(FI, "Sama nimi"), "2026-09-01");

    var model = build(List.of(broken, article));

    Assert.assertTrue(model.findArticlePage(Language.FI, "erc-1").isEmpty());
    Assert.assertFalse(locs(model).contains(FRONTEND_URL + "/fi/sama-nimi"));
  }

  @Test
  public void testItemOfAnotherLanguageCanTakeThePath() {
    // Without a Swedish name the UI routes the Finnish article at /sv/sama-nimi too, before the
    // Swedish one, as the languages share the /:lng route
    var finnish = article("erc-1", Map.of(FI, "Sama nimi"), "2026-09-01");
    var swedish = article("erc-2", Map.of(SV, "Sama nimi"), "2026-09-02");

    var model = build(List.of(finnish, swedish));

    Assert.assertTrue(locs(model).contains(FRONTEND_URL + "/fi/sama-nimi"));
    Assert.assertFalse(locs(model).contains(FRONTEND_URL + "/sv/sama-nimi"));
    Assert.assertTrue(model.findArticlePage(Language.SV, "erc-2").isEmpty());
  }

  @Test
  public void testCategoryLastmodIsTheNewestArticleOfItsSubtree() {
    var deepArticle = article("erc-1", Map.of(FI, "Syvä"), "2026-09-15");
    var subcategory = category("CategoryListing", Map.of(FI, "Ala"), deepArticle);
    var article = article("erc-2", Map.of(FI, "Pinta"), "2026-08-01");
    var untranslated = article("erc-3", Map.of(SV, "Nyaste"), "2026-10-01");
    var category = category("CategoryMain", Map.of(FI, "Pää"), subcategory, article, untranslated);
    var emptyCategory = category("CategoryMain", Map.of(FI, "Tyhjä"));

    var model = build(List.of(category, emptyCategory));

    Assert.assertEquals(
        LocalDate.parse("2026-09-15"), find(model, FRONTEND_URL + "/fi/paa").lastmod());
    Assert.assertEquals(
        LocalDate.parse("2026-09-15"), find(model, FRONTEND_URL + "/fi/paa/ala").lastmod());
    Assert.assertNull(find(model, FRONTEND_URL + "/fi/tyhja").lastmod());
  }

  @Test
  public void testLastmodIsInHelsinkiTime() {
    var id = ids.incrementAndGet();
    modifiedDates.put(id, Date.from(Instant.parse("2026-09-30T22:30:00Z")));
    var article = item("Article", "erc-1", Map.of(FI, "Artikkeli"), id, null, List.of());

    var model = build(List.of(article));

    Assert.assertEquals(
        LocalDate.parse("2026-10-01"), find(model, FRONTEND_URL + "/fi/artikkeli").lastmod());
  }

  @Test
  public void testArticlePagesAndContentUrls() {
    var article = article("erc/1 ä", Map.of(FI, "Artikkeli", EN, "Article"), "2026-09-30");
    var category = category("CategoryMain", Map.of(FI, "Kategoria", EN, "Category"), article);
    // The same article again under another category: the first placement is canonical
    var sameArticle =
        item("Article", "erc/1 ä", article.name_i18n(), article.articleId(), null, List.of());
    var otherCategory = category("CategoryMain", Map.of(FI, "Toinen"), sameArticle);

    var model = build(List.of(category, otherCategory));

    var page = model.findArticlePage(Language.FI, "erc/1 ä").orElseThrow();
    Assert.assertEquals(FRONTEND_URL + "/fi/kategoria/artikkeli", page.uiUrl());
    // The slash stays a path separator, as Tomcat rejects %2F
    Assert.assertEquals(CONTENT_URL + "/fi/erc/1%20%C3%A4", page.contentUrl());
    Assert.assertEquals(
        Map.of(
            "fi", FRONTEND_URL + "/fi/kategoria/artikkeli",
            "en", FRONTEND_URL + "/en/category/article",
            "x-default", FRONTEND_URL + "/fi/kategoria/artikkeli"),
        page.uiAlternates());
    // Only the canonical placement is listed, with the same language versions as the content page
    Assert.assertFalse(locs(model).contains(FRONTEND_URL + "/fi/toinen/artikkeli"));
    Assert.assertEquals(page.uiAlternates(), find(model, page.uiUrl()).alternates());
    Assert.assertTrue(locs(model).contains(FRONTEND_URL + "/fi/toinen"));

    Assert.assertEquals(2, model.contentUrls().size());
    var contentUrl = model.contentUrls().getFirst();
    Assert.assertEquals(page.contentUrl(), contentUrl.loc());
    Assert.assertEquals(LocalDate.parse("2026-09-30"), contentUrl.lastmod());
    Assert.assertEquals(
        Map.of(
            "fi", CONTENT_URL + "/fi/erc/1%20%C3%A4",
            "en", CONTENT_URL + "/en/erc/1%20%C3%A4",
            "x-default", CONTENT_URL + "/fi/erc/1%20%C3%A4"),
        contentUrl.alternates());
  }

  @Test
  public void testBreadcrumbsArePathOfTheCanonicalPlacementInTheLanguage() {
    var article = article("erc-1", Map.of(FI, "Artikkeli", SV, "Artikel"), "2026-09-30");
    var subcategory =
        category("CategoryListing", Map.of(FI, "Alakategoria", SV, "Underkategori"), article);
    var category = category("CategoryMain", Map.of(FI, "Kategoria", SV, "Kategori"), subcategory);
    var sameArticle =
        item("Article", "erc-1", article.name_i18n(), article.articleId(), null, List.of());
    var otherCategory = category("CategoryMain", Map.of(FI, "Toinen"), sameArticle);

    var model = build(List.of(category, otherCategory));

    Assert.assertEquals(
        List.of(
            new Breadcrumb("Ohjaajan osio", FRONTEND_URL + "/fi"),
            new Breadcrumb("Kategoria", FRONTEND_URL + "/fi/kategoria"),
            new Breadcrumb("Alakategoria", FRONTEND_URL + "/fi/kategoria/alakategoria")),
        model.findArticlePage(Language.FI, "erc-1").orElseThrow().breadcrumbs());
    Assert.assertEquals(
        List.of(
            new Breadcrumb("Vägledar-delen", FRONTEND_URL + "/sv"),
            new Breadcrumb("Kategori", FRONTEND_URL + "/sv/kategori"),
            new Breadcrumb("Underkategori", FRONTEND_URL + "/sv/kategori/underkategori")),
        model.findArticlePage(Language.SV, "erc-1").orElseThrow().breadcrumbs());
  }

  @Test
  public void testBreadcrumbOfACategoryTheUiDoesNotOpenHasNoUrl() {
    var first =
        category(
            "CategoryMain",
            Map.of(FI, "Sama nimi"),
            article("erc-1", Map.of(FI, "Eka"), "2026-09-01"));
    var article = article("erc-2", Map.of(FI, "Toka"), "2026-09-02");
    var second = category("CategoryMain", Map.of(FI, "Sama nimi"), article);

    var model = build(List.of(first, second));

    Assert.assertEquals(
        List.of(
            new Breadcrumb("Ohjaajan osio", FRONTEND_URL + "/fi"),
            new Breadcrumb("Sama nimi", null)),
        model.findArticlePage(Language.FI, "erc-2").orElseThrow().breadcrumbs());
  }

  private SitemapModel build(List<NavigationItemDto> items) {
    return new SitemapModelBuilder(FRONTEND_URL, CONTENT_URL, modifiedDates).build(items);
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

  private static List<String> locs(SitemapModel model) {
    return model.urls().stream().map(SitemapUrl::loc).toList();
  }

  private static SitemapUrl find(SitemapModel model, String loc) {
    return model.urls().stream()
        .filter(url -> url.loc().equals(loc))
        .findFirst()
        .orElseThrow(() -> new AssertionError("URL not in sitemap: " + loc));
  }
}
