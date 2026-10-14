/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.sitemap.service;

import java.time.LocalDate;
import java.util.List;

/**
 * The content of an article language version, as jod-ohjaaja-ui shows it on the article page.
 *
 * @param title the title
 * @param description the description as plain text, may be empty
 * @param ingress the ingress as plain text, may be empty
 * @param createDate the creation date, or null
 * @param modifiedDate the modification date, or null
 * @param imageDescription the description (alt text) of the image, may be empty
 * @param contentHtml the unsanitized rich text content, may be empty
 * @param documents the documents the guest may view
 * @param links the links with both a text and a URL
 * @param keywords the names of the article's tags
 */
public record ArticleContent(
    String title,
    String description,
    String ingress,
    LocalDate createDate,
    LocalDate modifiedDate,
    String imageDescription,
    String contentHtml,
    List<Link> documents,
    List<Link> links,
    List<String> keywords) {

  /**
   * @param text the link text
   * @param url the URL, absolute or relative to the service's origin
   */
  public record Link(String text, String url) {}
}
