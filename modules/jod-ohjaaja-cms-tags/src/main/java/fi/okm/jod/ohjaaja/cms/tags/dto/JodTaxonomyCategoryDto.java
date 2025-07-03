/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.tags.dto;

import java.util.Map;
import javax.validation.constraints.NotNull;

public record JodTaxonomyCategoryDto(
    @NotNull Long id,
    String externalReferenceCode,
    @NotNull String name,
    @NotNull Map<String, String> name_i18n,
    @NotNull JodCategoryType type) {}
