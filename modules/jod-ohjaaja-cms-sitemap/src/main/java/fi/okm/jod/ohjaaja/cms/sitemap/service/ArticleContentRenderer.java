/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.sitemap.service;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import fi.okm.jod.ohjaaja.cms.sitemap.model.ArticlePage;
import fi.okm.jod.ohjaaja.cms.sitemap.model.Breadcrumb;
import fi.okm.jod.ohjaaja.cms.sitemap.model.Language;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;

/**
 * Renders an article as a minimal HTML page without JS, for crawlers that do not run JS. The page
 * has the same content as the article page of jod-ohjaaja-ui, without the user specific functions,
 * and links to it as the canonical page.
 */
public class ArticleContentRenderer {

  private static final String[] ARTICLE_TAGS = {
    "p",
    "br",
    "strong",
    "b",
    "em",
    "i",
    "u",
    "s",
    "ul",
    "ol",
    "li",
    "h1",
    "h2",
    "h3",
    "h4",
    "a",
    "img",
    "blockquote",
    "hr",
    "sub",
    "sup",
    "div",
    "span",
    "h5",
    "h6",
    "figure",
    "figcaption",
    "pre",
    "code",
    "small",
    "abbr",
    "del",
    "ins",
    "dl",
    "dt",
    "dd",
    "table",
    "thead",
    "tbody",
    "tfoot",
    "tr",
    "th",
    "td",
    "caption",
    "colgroup",
    "col"
  };

  private static final String[] ARTICLE_ATTRIBUTES = {
    "title", "target", "rel", "alt", "width", "height", "loading", "lang", "dir", "colspan",
    "rowspan", "scope"
  };

  private static final String HTTP = "http";
  private static final String HTTPS = "https";

  // Only http(s) and relative URLs, like ALLOWED_URI_REGEXP in the UI
  private static final Safelist SAFELIST =
      Safelist.none()
          .addTags(ARTICLE_TAGS)
          .addAttributes(":all", ARTICLE_ATTRIBUTES)
          .addAttributes("a", "href")
          .addAttributes("img", "src")
          .addProtocols("a", "href", HTTP, HTTPS)
          .addProtocols("img", "src", HTTP, HTTPS)
          .preserveRelativeLinks(false);

  private static final Set<String> YOUTUBE_DOMAINS =
      Set.of("youtube.com", "youtu.be", "youtube-nocookie.com");

  private static final String IMAGE_COPYRIGHT_SEPARATOR = " Kuva: ";

  private static final Map<Language, Labels> LABELS =
      Map.of(
          Language.FI,
          new Labels(
              "Murupolku",
              "Luotu:",
              "Muokattu:",
              "Lisätietoja",
              "Avainsanat",
              "Katso video",
              "Lue Osaamispolussa"),
          Language.SV,
          new Labels(
              "Synlig sökväg",
              "Skapad:",
              "Redigerad:",
              "Ytterligare information",
              "Nyckelord",
              "Se videon",
              "Läs i Kompetensstigen"),
          Language.EN,
          new Labels(
              "Trail of breadcrumbs",
              "Created:",
              "Edited:",
              "Additional information",
              "Keywords",
              "Watch the video",
              "Read on Osaamispolku"));

  private static final Log log = LogFactoryUtil.getLog(ArticleContentRenderer.class);

  public String render(ArticleContent content, ArticlePage page) {
    var language = page.language();
    var labels = LABELS.get(language);
    var html = new StringBuilder(4096);

    html.append("<!DOCTYPE html>\n<html lang=\"").append(language.code()).append("\">\n<head>\n");
    html.append("<meta charset=\"utf-8\">\n");
    html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
    html.append("<title>").append(escape(content.title())).append("</title>\n");
    var description = content.description().isBlank() ? content.ingress() : content.description();
    if (!description.isBlank()) {
      html.append("<meta name=\"description\" content=\"")
          .append(escape(description.strip()))
          .append("\">\n");
    }
    html.append("<link rel=\"canonical\" href=\"").append(escape(page.uiUrl())).append("\">\n");
    page.uiAlternates()
        .forEach(
            (hreflang, href) ->
                html.append("<link rel=\"alternate\" hreflang=\"")
                    .append(escape(hreflang))
                    .append("\" href=\"")
                    .append(escape(href))
                    .append("\">\n"));
    appendBreadcrumbJsonLd(html, page.breadcrumbs(), content.title());
    html.append("</head>\n<body>\n");
    appendBreadcrumbNav(html, labels, page.breadcrumbs(), content.title());
    html.append("<main>\n<article>\n");

    html.append("<h1>").append(escape(content.title())).append("</h1>\n");
    appendDate(html, labels.created(), content.createDate(), language);
    appendDate(html, labels.modified(), content.modifiedDate(), language);

    if (!content.ingress().isBlank()) {
      html.append("<p>").append(escape(content.ingress().strip())).append("</p>\n");
    }

    appendImageDescription(html, content.imageDescription());

    var body = sanitize(content.contentHtml(), labels.video(), page.uiUrl());
    if (!body.isBlank()) {
      html.append("<div>\n").append(body).append("\n</div>\n");
    }

    appendDocumentsAndLinks(html, labels, content.documents(), content.links(), page.uiUrl());

    if (!content.keywords().isEmpty()) {
      html.append("<section>\n<h2>").append(escape(labels.keywords())).append("</h2>\n<ul>\n");
      for (var keyword : content.keywords()) {
        html.append("<li>").append(escape(keyword)).append("</li>\n");
      }
      html.append("</ul>\n</section>\n");
    }

    html.append("</article>\n<p><a href=\"")
        .append(escape(page.uiUrl()))
        .append("\">")
        .append(escape(labels.readInUi()))
        .append("</a></p>\n</main>\n</body>\n</html>\n");

    return html.toString();
  }

  /** The breadcrumb of the UI, ending in the article. A category without a URL is not a link. */
  private static void appendBreadcrumbNav(
      StringBuilder html, Labels labels, List<Breadcrumb> breadcrumbs, String title) {
    html.append("<nav aria-label=\"").append(escape(labels.breadcrumb())).append("\">\n<ol>\n");
    for (var breadcrumb : breadcrumbs) {
      html.append("<li>");
      if (breadcrumb.url() != null) {
        html.append("<a href=\"")
            .append(escape(breadcrumb.url()))
            .append("\">")
            .append(escape(breadcrumb.name()))
            .append("</a>");
      } else {
        html.append(escape(breadcrumb.name()));
      }
      html.append("</li>\n");
    }
    html.append("<li aria-current=\"page\">")
        .append(escape(title))
        .append("</li>\n</ol>\n</nav>\n");
  }

  /**
   * The breadcrumb as a schema.org BreadcrumbList. Every item but the last must have a URL, so the
   * categories without one are left out.
   */
  private static void appendBreadcrumbJsonLd(
      StringBuilder html, List<Breadcrumb> breadcrumbs, String title) {
    var items = new StringBuilder();
    var position = 0;
    for (var breadcrumb : breadcrumbs) {
      if (breadcrumb.url() != null) {
        appendListItem(items, ++position, breadcrumb.name(), breadcrumb.url());
      }
    }
    appendListItem(items, ++position, title, null);
    html.append(
            "<script type=\"application/ld+json\">{\"@context\":\"https://schema.org\","
                + "\"@type\":\"BreadcrumbList\",\"itemListElement\":[")
        .append(items)
        .append("]}</script>\n");
  }

  private static void appendListItem(StringBuilder json, int position, String name, String url) {
    if (position > 1) {
      json.append(',');
    }
    json.append("{\"@type\":\"ListItem\",\"position\":")
        .append(position)
        .append(",\"name\":")
        .append(jsonString(name));
    if (url != null) {
      json.append(",\"item\":").append(jsonString(url));
    }
    json.append('}');
  }

  /**
   * A JSON string that is safe inside a script element: also <, > and & are escaped, so that the
   * value cannot end the element.
   */
  static String jsonString(String value) {
    var json = new StringBuilder("\"");
    if (value != null) {
      for (var i = 0; i < value.length(); i++) {
        var c = value.charAt(i);
        switch (c) {
          case '"' -> json.append("\\\"");
          case '\\' -> json.append("\\\\");
          default -> {
            if (c < 0x20 || c == '<' || c == '>' || c == '&' || c == 0x2028 || c == 0x2029) {
              json.append(String.format("\\u%04x", (int) c));
            } else {
              json.append(c);
            }
          }
        }
      }
    }
    return json.append('"').toString();
  }

  /**
   * Sanitizes the article's rich text, replacing YouTube embeds with links to the video. As in the
   * UI, only the top level embeds are recognized.
   *
   * @param baseUrl the URL of the article in the UI: relative links are resolved against it, as the
   *     browser does on the UI page
   */
  String sanitize(String contentHtml, String videoLabel, String baseUrl) {
    if (contentHtml == null || contentHtml.isBlank()) {
      return "";
    }

    var document = Jsoup.parseBodyFragment(contentHtml, baseUrl);
    for (var element : document.body().children()) {
      var videoUrl = getYoutubeUrl(element);
      if (videoUrl != null) {
        var paragraph = new Element("p");
        paragraph.appendElement("a").attr("href", videoUrl).text(videoLabel + ": " + videoUrl);
        element.replaceWith(paragraph);
      }
    }

    return Jsoup.clean(document.body().html(), baseUrl, SAFELIST);
  }

  private static String getYoutubeUrl(Element element) {
    if (!element.hasClass("embed-responsive")) {
      return null;
    }
    var uri = toUri(element.attr("data-embed-id"));
    var host = uri != null ? uri.getHost() : null;
    if (host == null || !isHttp(uri)) {
      return null;
    }
    var lowerCaseHost = host.toLowerCase(Locale.ROOT);
    return YOUTUBE_DOMAINS.stream()
            .anyMatch(
                domain -> lowerCaseHost.equals(domain) || lowerCaseHost.endsWith("." + domain))
        ? uri.toString()
        : null;
  }

  private void appendImageDescription(StringBuilder html, String imageDescription) {
    if (imageDescription.isBlank()) {
      return;
    }
    // The UI shows the part after " Kuva: " as the image's copyright notice
    var parts = imageDescription.split(IMAGE_COPYRIGHT_SEPARATOR, -1);
    html.append("<figure>\n<figcaption>");
    if (parts.length == 2) {
      html.append(escape(parts[0].strip()))
          .append(" <small>© ")
          .append(escape(parts[1].strip()))
          .append("</small>");
    } else {
      html.append(escape(imageDescription.strip()));
    }
    html.append("</figcaption>\n</figure>\n");
  }

  private void appendDocumentsAndLinks(
      StringBuilder html,
      Labels labels,
      List<ArticleContent.Link> documents,
      List<ArticleContent.Link> links,
      String baseUrl) {

    var items = new StringBuilder();
    for (var link : documents) {
      appendLink(items, link, baseUrl);
    }
    for (var link : links) {
      appendLink(items, link, baseUrl);
    }
    if (items.isEmpty()) {
      return;
    }
    html.append("<section>\n<h2>")
        .append(escape(labels.additionalContent()))
        .append("</h2>\n<ul>\n")
        .append(items)
        .append("</ul>\n</section>\n");
  }

  private void appendLink(StringBuilder html, ArticleContent.Link link, String baseUrl) {
    var url = toSafeAbsoluteUrl(link.url(), baseUrl);
    if (url == null || link.text() == null || link.text().isBlank()) {
      return;
    }
    html.append("<li><a href=\"")
        .append(escape(url))
        .append("\">")
        .append(escape(link.text().strip()))
        .append("</a></li>\n");
  }

  /** Resolves the URL against the base URL and returns it only if it is an http(s) URL. */
  static String toSafeAbsoluteUrl(String url, String baseUrl) {
    var uri = toUri(url);
    if (uri == null) {
      return null;
    }
    var resolved = URI.create(baseUrl).resolve(uri);
    return isHttp(resolved) ? resolved.toString() : null;
  }

  /**
   * Parses a URL from the CMS. Editors enter URLs by hand, so characters that a URL may not
   * contain, such as spaces, are percent-encoded instead of dropping the link.
   */
  static URI toUri(String url) {
    if (url == null || url.isBlank()) {
      return null;
    }
    var stripped = url.strip();
    try {
      return new URI(stripped);
    } catch (URISyntaxException e) {
      try {
        return new URI(encodeIllegalCharacters(stripped));
      } catch (URISyntaxException e2) {
        log.warn("Leaving out an invalid URL: " + url);
        return null;
      }
    }
  }

  private static String encodeIllegalCharacters(String url) {
    var encoded = new StringBuilder(url.length() + 16);
    for (var b : url.getBytes(StandardCharsets.UTF_8)) {
      var c = b & 0xff;
      if (c <= 0x20 || c >= 0x7f || "\"<>\\^`{|}".indexOf(c) >= 0) {
        encoded.append('%').append(String.format("%02X", c));
      } else {
        encoded.append((char) c);
      }
    }
    return encoded.toString();
  }

  private static boolean isHttp(URI uri) {
    var scheme = uri.getScheme();
    return HTTP.equalsIgnoreCase(scheme) || HTTPS.equalsIgnoreCase(scheme);
  }

  private static void appendDate(
      StringBuilder html, String label, LocalDate date, Language language) {
    if (date == null) {
      return;
    }
    html.append("<p>")
        .append(escape(label))
        .append(" <time datetime=\"")
        .append(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
        .append("\">")
        .append(
            escape(
                date.format(
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                        .withLocale(language.locale()))))
        .append("</time></p>\n");
  }

  static String escape(String value) {
    if (value == null) {
      return "";
    }
    var escaped = new StringBuilder(value.length() + 16);
    for (var i = 0; i < value.length(); i++) {
      var c = value.charAt(i);
      switch (c) {
        case '&' -> escaped.append("&amp;");
        case '<' -> escaped.append("&lt;");
        case '>' -> escaped.append("&gt;");
        case '"' -> escaped.append("&quot;");
        case '\'' -> escaped.append("&#39;");
        default -> escaped.append(c);
      }
    }
    return escaped.toString();
  }

  private record Labels(
      String breadcrumb,
      String created,
      String modified,
      String additionalContent,
      String keywords,
      String video,
      String readInUi) {}
}
