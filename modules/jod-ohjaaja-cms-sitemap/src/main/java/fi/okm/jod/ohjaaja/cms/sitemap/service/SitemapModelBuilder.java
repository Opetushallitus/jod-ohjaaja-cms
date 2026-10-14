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
import fi.okm.jod.ohjaaja.cms.sitemap.model.Language;
import fi.okm.jod.ohjaaja.cms.sitemap.model.SitemapUrl;
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
 * Builds the URLs of sitemap.xml from the navigation tree. The tree must have been read with the
 * guest's permissions: items the guest may not view have no articleId or categoryId.
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

  // URL paths, not file paths
  private static final String PATH_SEPARATOR = "/";

  private static final Log log = LogFactoryUtil.getLog(SitemapModelBuilder.class);

  private final String frontendUrl;
  private final Map<Long, Date> articleModifiedDates;

  /**
   * @param frontendUrl the UI base URL without a trailing slash, e.g.
   *     https://osaamispolku.fi/ohjaaja
   * @param articleModifiedDates the modification date of the latest approved version of the
   *     navigation's articles by resourcePrimKey; articles without one are not published
   */
  public SitemapModelBuilder(String frontendUrl, Map<Long, Date> articleModifiedDates) {
    this.frontendUrl = frontendUrl;
    this.articleModifiedDates = articleModifiedDates;
  }

  /** The URLs of sitemap.xml. */
  public List<SitemapUrl> build(List<NavigationItemDto> navigationItems) {
    var routeOwners = getRouteOwners(navigationItems);

    var placements = new EnumMap<Language, Map<Long, Placement>>(Language.class);
    for (var language : Language.values()) {
      var languagePlacements = new LinkedHashMap<Long, Placement>();
      place(navigationItems, "", language, routeOwners, languagePlacements);
      placements.put(language, languagePlacements);
    }

    return getUrls(placements, getArticleUrls(placements));
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
   * @return the newest modification date of the published articles in the subtrees, or null
   */
  private LocalDate place(
      List<NavigationItemDto> items,
      String parentPath,
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

      if (item.id().equals(routeOwners.get(path))) {
        // Reserve the position before the children, so that the item is listed before them
        placements.put(item.id(), new Placement(item, path, ownLastmod));
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

      var childrenLastmod = place(item.children(), path, language, routeOwners, placements);
      if (!isArticle(item)) {
        // A category is as new as the newest article in its subtree
        placements.computeIfPresent(
            item.id(), (id, placement) -> new Placement(item, path, childrenLastmod));
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
      Map<Language, Map<Long, Placement>> placements,
      Map<String, Map<Language, String>> articleUrls) {

    var urls = new ArrayList<>(getFrontPageUrls());
    for (var language : Language.values()) {
      for (var placement : placements.get(language).values()) {
        var url = getUrl(language, placement, placements, articleUrls);
        if (url != null) {
          urls.add(url);
        }
      }
    }

    checkSize(urls.size());

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
      Map<String, Map<Language, String>> articleUrls) {

    var item = placement.item();
    var url = getUiUrl(language, placement.path());

    if (isArticle(item)) {
      var urls = articleUrls.get(item.externalReferenceCode());
      return url.equals(urls.get(language))
          ? new SitemapUrl(url, placement.lastmod(), getAlternates(urls))
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
   * The canonical UI URLs of the articles by ERC: the first placement of an article in a language
   * is its canonical page, if it is in the navigation more than once.
   */
  private Map<String, Map<Language, String>> getArticleUrls(
      Map<Language, Map<Long, Placement>> placements) {

    var articleUrls = new HashMap<String, Map<Language, String>>();
    for (var language : Language.values()) {
      for (var placement : placements.get(language).values()) {
        if (isArticle(placement.item())) {
          articleUrls
              .computeIfAbsent(
                  placement.item().externalReferenceCode(), erc -> new EnumMap<>(Language.class))
              .putIfAbsent(language, getUiUrl(language, placement.path()));
        }
      }
    }
    return articleUrls;
  }

  private static void checkSize(int urlCount) {
    if (urlCount > SitemapService.MAX_URLS) {
      log.error(
          "sitemap.xml has "
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

  private record Placement(NavigationItemDto item, String path, LocalDate lastmod) {}
}
