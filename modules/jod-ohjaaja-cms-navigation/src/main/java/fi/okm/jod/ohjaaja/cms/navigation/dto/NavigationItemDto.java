/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.navigation.dto;

import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;

public record NavigationItemDto(
    @NotNull Long id,
    @NotNull String name,
    @NotNull Map<String, String> name_i18n,
    @NotNull String description,
    @NotNull Map<String, String> description_i18n,
    @NotNull String type,
    Long articleId,
    Long categoryId,
    List<NavigationItemDto> children,
    Long parentId) {}
