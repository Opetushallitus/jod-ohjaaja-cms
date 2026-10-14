/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.sitemap.service;

/**
 * The sitemap of jod-ohjaaja-ui for search engines and chatbot crawlers. The navigation is read
 * with the guest's permissions, whoever the caller is, as the response is cached and shared.
 */
public interface SitemapService {

  /** A sitemap may have 50 000 URLs; above this an error is logged so there is time to react. */
  int MAX_URLS = 45_000;

  /** sitemap.xml: the canonical UI URLs. The array is shared and must not be modified. */
  byte[] getSitemapXml();

  /** Drops the cached sitemap, so that the next request rebuilds it. */
  void clearCache();
}
