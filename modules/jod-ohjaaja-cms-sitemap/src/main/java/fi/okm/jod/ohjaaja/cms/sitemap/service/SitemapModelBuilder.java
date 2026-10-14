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
import fi.okm.jod.ohjaaja.cms.navigation.dto.NavigationItemDto;
import fi.okm.jod.ohjaaja.cms.sitemap.model.ArticlePage;
import fi.okm.jod.ohjaaja.cms.sitemap.model.Breadcrumb;
import fi.okm.jod.ohjaaja.cms.sitemap.model.Language;
import fi.okm.jod.ohjaaja.cms.sitemap.model.SitemapModel;
import fi.okm.jod.ohjaaja.cms.sitemap.model.SitemapUrl;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the sitemap model from the navigation tree. The tree must have been read with the guest's
 * permissions: items the guest may not view have no articleId or categoryId.
 *
 * <p>An item is published in a language only if it and all of its ancestors have a name in that
 * language. The UI falls back to the name in the language the service was opened in, so such URLs
 * would not be stable. An item is also left out if the UI router would open another item at its
 * URL.
 */
public class SitemapModelBuilder {

  static final String TYPE_ARTICLE = "Article";

  private static final Set<String> CATEGORY_TYPES =
      Set.of("CategoryMain", "CategoryListing", "StudyProgramsListing");

  // The front page's name in the UI's breadcrumb (front-page in src/i18n/ohjaaja)
  private static final Map<Language, String> FRONT_PAGE_NAMES =
      Map.of(
          Language.FI,
          "Ohjaajan osio",
          Language.SV,
          "Vägledar-delen",
          Language.EN,
          "Guidance practitioner section");

  // URL paths, not file paths
  private static final String PATH_SEPARATOR = "/";

  private static final Log log = LogFactoryUtil.getLog(SitemapModelBuilder.class);

  private final String frontendUrl;
  private final String contentUrl;
  private final Map<Long, Date> articleModifiedDates;

  /**
   * @param frontendUrl the UI base URL without a trailing slash, e.g.
   *     https://osaamispolku.fi/ohjaaja
   * @param contentUrl the public URL of the content pages without a trailing slash, e.g.
   *     https://osaamispolku.fi/ohjaaja/cms/o/jod-sitemap/content
   * @param articleModifiedDates the modification date of the latest approved version of the
   *     navigation's articles by resourcePrimKey; articles without one are not published
   */
  public SitemapModelBuilder(
      String frontendUrl, String contentUrl, Map<Long, Date> articleModifiedDates) {
    this.frontendUrl = frontendUrl;
    this.contentUrl = contentUrl;
    this.articleModifiedDates = articleModifiedDates;
  }

  public SitemapModel build(List<NavigationItemDto> navigationItems) {
    var routeOwners = getRouteOwners(navigationItems);

    var placements = new EnumMap<Language, Map<Long, Placement>>(Language.class);
    for (var language : Language.values()) {
      var languagePlacements = new LinkedHashMap<Long, Placement>();
      var frontPage = new Breadcrumb(FRONT_PAGE_NAMES.get(language), getUiUrl(language, ""));
      place(navigationItems, "", List.of(frontPage), language, routeOwners, languagePlacements);
      placements.put(language, languagePlacements);
    }

    var articlePages = getArticlePages(placements);

    return new SitemapModel(
        getUrls(placements, articlePages),
        getContentUrls(articlePages),
        Collections.unmodifiableMap(articlePages));
  }

  static Map<String, Long> getRouteOwners(List<NavigationItemDto> navigationItems) {
    var withChildren = new HashMap<String, Long>();
    var leaves = new HashMap<String, Long>();
    for (var language : Language.values()) {
      addRoutes(navigationItems, "", language, withChildren, leaves);
    }
    var owners = new HashMap<>(leaves);
    owners.putAll(withChildren);
    return owners;
  }

  private static void addRoutes(
      List<NavigationItemDto> items,
      String parentPath,
      Language language,
      Map<String, Long> withChildren,
      Map<String, Long> leaves) {

    if (items == null) {
      return;
    }
    for (var item : items) {
      var slug = getRouteSlug(item, language);
      if (slug.isEmpty()) {
        continue;
      }
      var path = parentPath + PATH_SEPARATOR + slug;
      var children = item.children() != null ? item.children() : List.<NavigationItemDto>of();
      (children.isEmpty() ? leaves : withChildren).putIfAbsent(path, item.id());
      addRoutes(children, path, language, withChildren, leaves);
    }
  }

  /** The path segment the UI routes the item with (src/utils/navigation.ts). */
  private static String getRouteSlug(NavigationItemDto item, Language language) {
    var slug = item.slug_i18n() != null ? item.slug_i18n().get(language.languageTag()) : null;
    if (slug == null || slug.isEmpty()) {
      slug = item.slug();
    }
    return slug != null ? slug : "";
  }

  /**
   * Places the publishable items in depth-first order, so that the sitemap lists them the way the
   * navigation does.
   *
   * @param breadcrumbs the front page and the published categories on the path to the items
   * @return the newest modification date of the published articles in the subtrees, or null
   */
  private LocalDate place(
      List<NavigationItemDto> items,
      String parentPath,
      List<Breadcrumb> breadcrumbs,
      Language language,
      Map<String, Long> routeOwners,
      Map<Long, Placement> placements) {

    if (items == null) {
      return null;
    }

    LocalDate newest = null;

    for (var item : items) {
      var slug = getPublishableSlug(item, language);
      if (slug == null) {
        continue;
      }

      var path = parentPath + PATH_SEPARATOR + slug;
      var ownLastmod = isArticle(item) ? getLastmod(item.articleId()) : null;
      var isRouteOwner = item.id().equals(routeOwners.get(path));

      if (isRouteOwner) {
        // Reserve the position before the children, so that the item is listed before them
        placements.put(item.id(), new Placement(item, path, breadcrumbs, ownLastmod));
      } else {
        // The children have paths of their own and may still be opened
        log.warn(
            "Leaving out navigation item "
                + item.externalReferenceCode()
                + " in language "
                + language.code()
                + ": the UI opens navigation item "
                + routeOwners.get(path)
                + " at the same path "
                + path);
      }

      var childBreadcrumbs = new ArrayList<>(breadcrumbs);
      childBreadcrumbs.add(
          new Breadcrumb(
              item.name_i18n().get(language.languageTag()),
              isRouteOwner ? getUiUrl(language, path) : null));
      var childrenLastmod =
          place(
              item.children(),
              path,
              Collections.unmodifiableList(childBreadcrumbs),
              language,
              routeOwners,
              placements);
      if (!isArticle(item)) {
        // A category is as new as the newest article in its subtree
        placements.computeIfPresent(
            item.id(), (id, placement) -> new Placement(item, path, breadcrumbs, childrenLastmod));
      }
      newest = newest(newest, newest(ownLastmod, childrenLastmod));
    }

    return newest;
  }

  private String getPublishableSlug(NavigationItemDto item, Language language) {
    if (isArticle(item)) {
      if (item.articleId() == null || !articleModifiedDates.containsKey(item.articleId())) {
        return null;
      }
    } else if (!CATEGORY_TYPES.contains(item.type()) || item.categoryId() == null) {
      return null;
    }

    // An article's names are its title translations, i.e. the languages it is available in
    var name = item.name_i18n() != null ? item.name_i18n().get(language.languageTag()) : null;
    if (name == null || name.isBlank()) {
      return null;
    }

    var slug = item.slug_i18n() != null ? item.slug_i18n().get(language.languageTag()) : null;
    return (slug == null || slug.isEmpty()) ? null : slug;
  }

  /**
   * An article in the navigation more than once is listed only at its canonical page, with the same
   * language versions as its content page.
   */
  private List<SitemapUrl> getUrls(
      Map<Language, Map<Long, Placement>> placements, Map<String, ArticlePage> articlePages) {

    var urls = new ArrayList<>(getFrontPageUrls());
    for (var language : Language.values()) {
      for (var placement : placements.get(language).values()) {
        var url = getUrl(language, placement, placements, articlePages);
        if (url != null) {
          urls.add(url);
        }
      }
    }

    checkSize("sitemap.xml", urls.size());

    return Collections.unmodifiableList(urls);
  }

  private List<SitemapUrl> getFrontPageUrls() {
    var frontPageUrls =
        Arrays.stream(Language.values())
            .collect(
                Collectors.toMap(
                    language -> language,
                    language -> getUiUrl(language, ""),
                    (a, b) -> a,
                    () -> new EnumMap<>(Language.class)));
    var alternates = getAlternates(frontPageUrls);
    return frontPageUrls.values().stream()
        .map(url -> new SitemapUrl(url, null, alternates))
        .toList();
  }

  /** The URL of a placement, or null if it is not listed. */
  private SitemapUrl getUrl(
      Language language,
      Placement placement,
      Map<Language, Map<Long, Placement>> placements,
      Map<String, ArticlePage> articlePages) {

    var item = placement.item();
    var url = getUiUrl(language, placement.path());

    if (isArticle(item)) {
      var articlePage = articlePages.get(SitemapModel.key(language, item.externalReferenceCode()));
      return url.equals(articlePage.uiUrl())
          ? new SitemapUrl(url, placement.lastmod(), articlePage.uiAlternates())
          : null;
    }

    var itemUrls = new EnumMap<Language, String>(Language.class);
    for (var alternateLanguage : Language.values()) {
      var alternate = placements.get(alternateLanguage).get(item.id());
      if (alternate != null) {
        itemUrls.put(alternateLanguage, getUiUrl(alternateLanguage, alternate.path()));
      }
    }
    return new SitemapUrl(url, placement.lastmod(), getAlternates(itemUrls));
  }

  /**
   * The first placement of an article in a language is its canonical page, if it is in the
   * navigation more than once.
   */
  private Map<String, ArticlePage> getArticlePages(Map<Language, Map<Long, Placement>> placements) {

    var uiUrls = new LinkedHashMap<String, Map<Language, String>>();
    for (var language : Language.values()) {
      for (var placement : placements.get(language).values()) {
        if (isArticle(placement.item())) {
          uiUrls
              .computeIfAbsent(
                  placement.item().externalReferenceCode(), erc -> new EnumMap<>(Language.class))
              .putIfAbsent(language, getUiUrl(language, placement.path()));
        }
      }
    }

    var articlePages = new LinkedHashMap<String, ArticlePage>();
    for (var language : Language.values()) {
      for (var placement : placements.get(language).values()) {
        var item = placement.item();
        var erc = item.externalReferenceCode();
        if (!isArticle(item) || articlePages.containsKey(SitemapModel.key(language, erc))) {
          continue;
        }
        var articleUrls = uiUrls.get(erc);
        articlePages.put(
            SitemapModel.key(language, erc),
            new ArticlePage(
                erc,
                item.articleId(),
                language,
                articleUrls.get(language),
                getAlternates(articleUrls),
                placement.breadcrumbs(),
                getContentUrl(language, erc),
                placement.lastmod()));
      }
    }
    return articlePages;
  }

  private List<SitemapUrl> getContentUrls(Map<String, ArticlePage> articlePages) {
    var contentUrls = new ArrayList<SitemapUrl>();
    for (var articlePage : articlePages.values()) {
      var erc = articlePage.externalReferenceCode();
      var urls = new EnumMap<Language, String>(Language.class);
      for (var language : Language.values()) {
        if (articlePages.containsKey(SitemapModel.key(language, erc))) {
          urls.put(language, getContentUrl(language, erc));
        }
      }
      contentUrls.add(
          new SitemapUrl(articlePage.contentUrl(), articlePage.lastmod(), getAlternates(urls)));
    }
    checkSize("content-sitemap.xml", contentUrls.size());
    return Collections.unmodifiableList(contentUrls);
  }

  private static void checkSize(String sitemap, int urlCount) {
    if (urlCount > SitemapService.MAX_URLS) {
      log.error(
          sitemap
              + " has "
              + urlCount
              + " URLs, more than "
              + SitemapService.MAX_URLS
              + ". A sitemap index is needed, as a sitemap may have at most 50 000 URLs.");
    }
  }

  /** hreflang to URL of the language versions, with x-default when there is a default version. */
  private static Map<String, String> getAlternates(Map<Language, String> urls) {
    var alternates = new LinkedHashMap<String, String>();
    urls.forEach((language, url) -> alternates.put(language.code(), url));
    var defaultUrl = urls.get(Language.DEFAULT);
    if (defaultUrl != null) {
      alternates.put("x-default", defaultUrl);
    }
    return Collections.unmodifiableMap(alternates);
  }

  private String getUiUrl(Language language, String path) {
    return frontendUrl + PATH_SEPARATOR + language.code() + path;
  }

  /**
   * The ERC's slashes are kept as path separators, as Tomcat rejects an encoded slash (%2F). The
   * content page route takes the rest of the path as the ERC.
   */
  private String getContentUrl(Language language, String externalReferenceCode) {
    var encodedExternalReferenceCode =
        Arrays.stream(externalReferenceCode.split(PATH_SEPARATOR, -1))
            .map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"))
            .collect(Collectors.joining(PATH_SEPARATOR));
    return contentUrl
        + PATH_SEPARATOR
        + language.code()
        + PATH_SEPARATOR
        + encodedExternalReferenceCode;
  }

  private LocalDate getLastmod(Long articleId) {
    return SitemapDates.toLocalDate(articleModifiedDates.get(articleId));
  }

  private static boolean isArticle(NavigationItemDto item) {
    return TYPE_ARTICLE.equals(item.type());
  }

  private static LocalDate newest(LocalDate a, LocalDate b) {
    if (a == null) {
      return b;
    }
    return (b == null || a.isAfter(b)) ? a : b;
  }

  private record Placement(
      NavigationItemDto item, String path, List<Breadcrumb> breadcrumbs, LocalDate lastmod) {}
}
