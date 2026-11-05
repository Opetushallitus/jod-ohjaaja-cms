/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.tags.service;

import fi.okm.jod.ohjaaja.cms.tags.dto.JodTaxonomyCategoryDto;
import java.util.List;
import java.util.Map;

public interface TagsService {

  /** Returns the tags of the site, or an empty list if the site has no tag vocabulary. */
  List<JodTaxonomyCategoryDto> getJodTaxonomyCategories(Long siteId);

  /**
   * Creates the tag vocabulary of the JOD Ohjaaja site if it does not exist and grants the guest
   * the view permission on it. A vocabulary named "tags" without the external reference code is
   * taken over instead of creating another one. Run when the service is activated, so the
   * vocabulary exists before the service is used.
   */
  void initTagVocabulary();

  void addOrUpdateJodTaxonomyCategory(
      Long categoryId,
      String externalReferenceCode,
      String name,
      Map<String, String> nameI18n,
      Long siteId);
}
