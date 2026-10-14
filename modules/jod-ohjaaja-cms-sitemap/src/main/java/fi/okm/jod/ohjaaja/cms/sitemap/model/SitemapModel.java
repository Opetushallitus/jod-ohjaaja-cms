/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.sitemap.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Everything the sitemap endpoints serve, built from the navigation tree as seen by the guest.
 *
 * @param urls the UI URLs of sitemap.xml
 * @param contentUrls the content page URLs of content-sitemap.xml
 * @param articlePages the published article language versions by {@link #key}
 */
public record SitemapModel(
    List<SitemapUrl> urls, List<SitemapUrl> contentUrls, Map<String, ArticlePage> articlePages) {

  public Optional<ArticlePage> findArticlePage(Language language, String externalReferenceCode) {
    return Optional.ofNullable(articlePages.get(key(language, externalReferenceCode)));
  }

  public static String key(Language language, String externalReferenceCode) {
    return language.code() + "/" + externalReferenceCode;
  }
}
