/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.navigation.dto;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public record NavigationItemDto(
    @NotNull Long id,
    @NotNull String name,
    @NotNull Map<String, String> name_i18n,
    @NotNull String slug,
    @NotNull Map<String, String> slug_i18n,
    @NotNull String description,
    @NotNull Map<String, String> description_i18n,
    @NotNull String type,
    boolean hideFromHomePageNewestCarousel,
    boolean hideFromHomePageMostViewedCarousel,
    boolean hideFromMainCategoryPageNewestCarousel,
    boolean hideFromMainCategoryPageMostViewedCarousel,
    @NotNull String externalReferenceCode,
    Long articleId,
    Long categoryId,
    List<NavigationItemDto> children,
    Long parentId)
    implements Serializable {

  // The navigation is cached and shared by all requests, so it must not be modifiable
  public NavigationItemDto {
    name_i18n = unmodifiable(name_i18n);
    slug_i18n = unmodifiable(slug_i18n);
    description_i18n = unmodifiable(description_i18n);
    children = children == null ? null : Collections.unmodifiableList(children);
  }

  private static Map<String, String> unmodifiable(Map<String, String> map) {
    return map == null ? null : Collections.unmodifiableMap(map);
  }
}
