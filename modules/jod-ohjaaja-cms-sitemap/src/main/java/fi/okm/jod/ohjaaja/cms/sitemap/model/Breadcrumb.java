/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.sitemap.model;

/**
 * The front page or a category on the path to an article in the navigation, as in the UI's
 * breadcrumb.
 *
 * @param name the name in the page's language
 * @param url the URL in the UI, or null if the UI opens another item at the category's path
 */
public record Breadcrumb(String name, String url) {}
