/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.sitemap.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * A published language version of an article, served as a lightweight content page.
 *
 * @param externalReferenceCode the article's ERC
 * @param resourcePrimKey the article's resourcePrimKey
 * @param language the language version
 * @param uiUrl the canonical URL of the article in the UI
 * @param uiAlternates hreflang to UI URL of every language version of the article
 * @param breadcrumbs the front page and the categories on the path to the canonical page
 * @param contentUrl the URL of the content page
 * @param lastmod the modification date of the latest approved version
 */
public record ArticlePage(
    String externalReferenceCode,
    long resourcePrimKey,
    Language language,
    String uiUrl,
    Map<String, String> uiAlternates,
    List<Breadcrumb> breadcrumbs,
    String contentUrl,
    LocalDate lastmod) {}
