/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.sitemap.service;

import fi.okm.jod.ohjaaja.cms.sitemap.model.ArticlePage;
import fi.okm.jod.ohjaaja.cms.sitemap.model.Breadcrumb;
import fi.okm.jod.ohjaaja.cms.sitemap.model.Language;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import org.jsoup.Jsoup;
import org.junit.Assert;
import org.junit.Test;

/** Unit tests for the content page HTML and the sanitization of the article's rich text. */
public class ArticleContentRendererTest {

  private static final String FRONTEND_URL = "https://osaamispolku.fi/ohjaaja";

  private static final String ARTICLE_URL = FRONTEND_URL + "/fi/kategoria/artikkeli";

  private final ArticleContentRenderer renderer = new ArticleContentRenderer();

  @Test
  public void testDisallowedTagsAttributesAndProtocolsAreRemoved() {
    var html =
        renderer.sanitize(
            "<p class=\"x\" style=\"color:red\" data-x=\"1\" onclick=\"alert(1)\">Teksti"
                + "<script>alert(1)</script><iframe src=\"https://evil.example\"></iframe></p>"
                + "<a href=\"javascript:alert(1)\">js</a>"
                + "<a href=\"mailto:a@example.com\">mail</a>"
                + "<table><tr><td colspan=\"2\">solu</td></tr></table>",
            "Katso video",
            ARTICLE_URL);

    var body = Jsoup.parseBodyFragment(html).body();
    Assert.assertTrue(
        body.select("script, iframe, [class], [style], [data-x], [onclick]").isEmpty());
    Assert.assertEquals("Teksti", body.selectFirst("p").text());
    Assert.assertTrue(body.select("a[href]").isEmpty());
    Assert.assertEquals("2", body.selectFirst("td").attr("colspan"));
  }

  @Test
  public void testRelativeLinksAreResolvedAgainstTheArticleInTheUi() {
    var html =
        renderer.sanitize(
            "<a href=\"/ohjaaja/fi/haku\">haku</a>"
                + "<a href=\"liite.pdf\">liite</a>"
                + "<a href=\"#osio2\">osio</a>"
                + "<img src=\"/ohjaaja/cms/documents/1/2/kuva.png\" alt=\"kuva\">",
            "Katso video",
            ARTICLE_URL);

    var body = Jsoup.parseBodyFragment(html).body();
    Assert.assertEquals(
        List.of(
            "https://osaamispolku.fi/ohjaaja/fi/haku",
            FRONTEND_URL + "/fi/kategoria/liite.pdf",
            ARTICLE_URL + "#osio2"),
        body.select("a").eachAttr("href"));
    Assert.assertEquals(
        "https://osaamispolku.fi/ohjaaja/cms/documents/1/2/kuva.png",
        body.selectFirst("img").attr("src"));
  }

  @Test
  public void testDocumentAndLinkUrlsAreResolvedAndEncoded() {
    Assert.assertEquals(
        "https://osaamispolku.fi/ohjaaja/cms/documents/1/2/Liite%20A.pdf/uuid",
        ArticleContentRenderer.toSafeAbsoluteUrl(
            "/ohjaaja/cms/documents/1/2/Liite A.pdf/uuid", ARTICLE_URL));
    Assert.assertEquals(
        "https://example.com/haku?q=%C3%A4%20%C3%B6",
        ArticleContentRenderer.toSafeAbsoluteUrl(" https://example.com/haku?q=ä ö ", ARTICLE_URL));
    Assert.assertEquals(
        FRONTEND_URL + "/fi/kategoria/toinen",
        ArticleContentRenderer.toSafeAbsoluteUrl("toinen", ARTICLE_URL));
    Assert.assertNull(ArticleContentRenderer.toSafeAbsoluteUrl("javascript:alert(1)", ARTICLE_URL));
    Assert.assertNull(
        ArticleContentRenderer.toSafeAbsoluteUrl("mailto:a@example.com", ARTICLE_URL));
    Assert.assertNull(ArticleContentRenderer.toSafeAbsoluteUrl(" ", ARTICLE_URL));
  }

  @Test
  public void testYoutubeEmbedIsReplacedWithALink() {
    var html =
        renderer.sanitize(
            "<p>Ennen</p><div class=\"embed-responsive\" data-embed-id=\"https://www.youtube.com/embed/abc\">"
                + "<iframe src=\"https://www.youtube.com/embed/abc\"></iframe></div>"
                + "<div class=\"embed-responsive\" data-embed-id=\"https://evil.example/embed\">Muu</div>",
            "Katso video",
            ARTICLE_URL);

    var body = Jsoup.parseBodyFragment(html).body();
    var links = body.select("a");
    Assert.assertEquals(1, links.size());
    Assert.assertEquals("https://www.youtube.com/embed/abc", links.first().attr("href"));
    Assert.assertEquals("Katso video: https://www.youtube.com/embed/abc", links.first().text());
    Assert.assertTrue(body.select("iframe").isEmpty());
    Assert.assertTrue(body.text().contains("Muu"));
  }

  @Test
  public void testYoutubeEmbedsOfOtherYoutubeHostsAndWithSpacesAreReplaced() {
    var html =
        renderer.sanitize(
            "<div class=\"embed-responsive\" data-embed-id=\"https://m.youtube.com/watch?v=a\"></div>"
                + "<div class=\"embed-responsive\" data-embed-id=\"https://youtube-nocookie.com/embed/b\"></div>"
                + "<div class=\"embed-responsive\" data-embed-id=\" https://youtu.be/c?si=x y\"></div>"
                + "<div class=\"embed-responsive\" data-embed-id=\"https://notyoutube.com/embed/d\">Muu</div>",
            "Katso video",
            ARTICLE_URL);

    Assert.assertEquals(
        List.of(
            "https://m.youtube.com/watch?v=a",
            "https://youtube-nocookie.com/embed/b",
            "https://youtu.be/c?si=x%20y"),
        Jsoup.parseBodyFragment(html).body().select("a").eachAttr("href"));
  }

  @Test
  public void testPageHasCanonicalAlternatesAndContentInOrder() {
    var alternates = new LinkedHashMap<String, String>();
    alternates.put("fi", FRONTEND_URL + "/fi/kategoria/artikkeli");
    alternates.put("sv", FRONTEND_URL + "/sv/kategori/artikel");
    alternates.put("x-default", FRONTEND_URL + "/fi/kategoria/artikkeli");
    var page =
        new ArticlePage(
            "erc-1",
            1L,
            Language.FI,
            FRONTEND_URL + "/fi/kategoria/artikkeli",
            alternates,
            List.of(new Breadcrumb("Ohjaajan osio", FRONTEND_URL + "/fi")),
            FRONTEND_URL + "/cms/o/jod-sitemap/content/fi/erc-1",
            LocalDate.parse("2026-09-30"));
    var content =
        new ArticleContent(
            "Otsikko <b>&</b>",
            "",
            "Ingressi \"lainaus\"",
            LocalDate.parse("2026-01-02"),
            LocalDate.parse("2026-09-30"),
            "Kuvaus kuvasta Kuva: Matti Meikäläinen",
            "<p>Sisältö</p>",
            List.of(
                new ArticleContent.Link("Liite.pdf", "/ohjaaja/cms/documents/1/2/Liite.pdf/uuid")),
            List.of(
                new ArticleContent.Link("Linkki", "https://example.com/"),
                new ArticleContent.Link("Huono", "javascript:alert(1)")),
            List.of("Avainsana"));

    var html = renderer.render(content, page);
    var document = Jsoup.parse(html);

    Assert.assertFalse(html.contains("<b>&</b>"));
    Assert.assertEquals("fi", document.selectFirst("html").attr("lang"));
    Assert.assertEquals("Otsikko <b>&</b>", document.title());
    Assert.assertEquals(
        "Ingressi \"lainaus\"", document.selectFirst("meta[name=description]").attr("content"));
    Assert.assertEquals(
        FRONTEND_URL + "/fi/kategoria/artikkeli",
        document.selectFirst("link[rel=canonical]").attr("href"));
    Assert.assertEquals(3, document.select("link[rel=alternate]").size());
    Assert.assertEquals(
        FRONTEND_URL + "/sv/kategori/artikel",
        document.selectFirst("link[hreflang=sv]").attr("href"));
    // Only the breadcrumb's data, no executable scripts
    Assert.assertEquals(List.of("application/ld+json"), document.select("script").eachAttr("type"));

    Assert.assertEquals("Otsikko <b>&</b>", document.selectFirst("h1").text());
    Assert.assertEquals("2026-01-02", document.select("time").get(0).attr("datetime"));
    Assert.assertEquals("2026-09-30", document.select("time").get(1).attr("datetime"));
    Assert.assertEquals(
        "Kuvaus kuvasta © Matti Meikäläinen", document.selectFirst("figcaption").text());

    var articleText = document.selectFirst("article").text();
    Assert.assertTrue(
        articleText.indexOf("Ingressi") < articleText.indexOf("Kuvaus kuvasta")
            && articleText.indexOf("Kuvaus kuvasta") < articleText.indexOf("Sisältö")
            && articleText.indexOf("Sisältö") < articleText.indexOf("Liite.pdf")
            && articleText.indexOf("Liite.pdf") < articleText.indexOf("Linkki")
            && articleText.indexOf("Linkki") < articleText.indexOf("Avainsana"));

    var linkHrefs = document.select("section a").eachAttr("href");
    Assert.assertEquals(
        List.of(
            "https://osaamispolku.fi/ohjaaja/cms/documents/1/2/Liite.pdf/uuid",
            "https://example.com/"),
        linkHrefs);

    var readInUi = document.select("main > p > a").last();
    Assert.assertEquals("Lue Osaamispolussa", readInUi.text());
    Assert.assertEquals(FRONTEND_URL + "/fi/kategoria/artikkeli", readInUi.attr("href"));
  }

  @Test
  public void testEmptyFieldsAreLeftOut() {
    var page =
        new ArticlePage(
            "erc-1",
            1L,
            Language.EN,
            FRONTEND_URL + "/en/article",
            new LinkedHashMap<>(),
            List.of(new Breadcrumb("Guidance practitioner section", FRONTEND_URL + "/en")),
            FRONTEND_URL + "/cms/o/jod-sitemap/content/en/erc-1",
            null);
    var content =
        new ArticleContent("Title", "", "", null, null, "", "", List.of(), List.of(), List.of());

    var document = Jsoup.parse(renderer.render(content, page));

    Assert.assertNull(document.selectFirst("meta[name=description]"));
    Assert.assertTrue(document.select("time, figure, section").isEmpty());
    Assert.assertEquals("Read on Osaamispolku", document.select("main > p > a").last().text());
  }

  @Test
  public void testBreadcrumbIsRenderedAsNavAndJsonLd() {
    var page =
        new ArticlePage(
            "erc-1",
            1L,
            Language.FI,
            ARTICLE_URL,
            new LinkedHashMap<>(),
            List.of(
                new Breadcrumb("Ohjaajan osio", FRONTEND_URL + "/fi"),
                new Breadcrumb("Piilotettu", null),
                new Breadcrumb("Kategoria </script> \"&\"", FRONTEND_URL + "/fi/kategoria")),
            FRONTEND_URL + "/cms/o/jod-sitemap/content/fi/erc-1",
            null);
    var content =
        new ArticleContent(
            "Artikkeli", "", "", null, null, "", "", List.of(), List.of(), List.of());

    var html = renderer.render(content, page);
    var document = Jsoup.parse(html);

    var nav = document.selectFirst("body > nav[aria-label=Murupolku]");
    Assert.assertNotNull(nav);
    Assert.assertEquals(
        List.of("Ohjaajan osio", "Piilotettu", "Kategoria </script> \"&\"", "Artikkeli"),
        nav.select("li").eachText());
    Assert.assertEquals(
        List.of(FRONTEND_URL + "/fi", FRONTEND_URL + "/fi/kategoria"),
        nav.select("a").eachAttr("href"));
    Assert.assertEquals("page", nav.select("li").last().attr("aria-current"));

    var jsonLd = document.selectFirst("head > script[type=application/ld+json]");
    Assert.assertNotNull(jsonLd);
    // The category name cannot end the script element
    Assert.assertEquals(1, html.split("</script>", -1).length - 1);
    Assert.assertEquals(
        "{\"@context\":\"https://schema.org\",\"@type\":\"BreadcrumbList\",\"itemListElement\":["
            + "{\"@type\":\"ListItem\",\"position\":1,\"name\":\"Ohjaajan osio\",\"item\":\""
            + FRONTEND_URL
            + "/fi\"},"
            + "{\"@type\":\"ListItem\",\"position\":2,"
            + "\"name\":\"Kategoria \\u003c/script\\u003e \\\"\\u0026\\\"\",\"item\":\""
            + FRONTEND_URL
            + "/fi/kategoria\"},"
            + "{\"@type\":\"ListItem\",\"position\":3,\"name\":\"Artikkeli\"}]}",
        jsonLd.data());
  }
}
