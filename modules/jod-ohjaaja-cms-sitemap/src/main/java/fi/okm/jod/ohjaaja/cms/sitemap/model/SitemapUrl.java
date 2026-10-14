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
import java.util.Map;

/**
 * One {@code <url>} element of a sitemap.
 *
 * @param loc the URL
 * @param lastmod the last modification date, or null if it is omitted
 * @param alternates hreflang to URL of every language version, including this one and x-default
 */
public record SitemapUrl(String loc, LocalDate lastmod, Map<String, String> alternates) {}
