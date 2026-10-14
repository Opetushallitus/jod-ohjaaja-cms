/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.sitemap.service;

import java.util.Optional;

/**
 * Sitemaps of jod-ohjaaja-ui for search engines and chatbot crawlers. All content is read with the
 * guest's permissions, whoever the caller is, as the responses are cached and shared.
 */
public interface SitemapService {

  /** A sitemap may have 50 000 URLs; above this an error is logged so there is time to react. */
  int MAX_URLS = 45_000;

  /** sitemap.xml: the canonical UI URLs. The array is shared and must not be modified. */
  byte[] getSitemapXml();

  /**
   * content-sitemap.xml: the URLs of the content pages, for crawlers that do not run JS. The array
   * is shared and must not be modified.
   */
  byte[] getContentSitemapXml();

  /**
   * A lightweight HTML page of a published article.
   *
   * @param languageCode fi, sv or en
   * @param externalReferenceCode the article's ERC
   * @return the page, or empty if the article is not published in the navigation in the language
   */
  Optional<String> getContentPage(String languageCode, String externalReferenceCode);

  /** Drops the cached model, so that the next request rebuilds it. */
  void clearCache();
}
