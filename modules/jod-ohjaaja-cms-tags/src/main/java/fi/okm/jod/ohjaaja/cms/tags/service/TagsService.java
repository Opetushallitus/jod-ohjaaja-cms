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
  List<JodTaxonomyCategoryDto> getJodTaxonomyCategories(Long siteId);

  void addOrUpdateJodTaxonomyCategory(
      Long categoryId,
      String externalReferenceCode,
      String name,
      Map<String, String> name_i18n,
      Long siteId);
}
