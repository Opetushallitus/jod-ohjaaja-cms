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

/**
 * @param contentStructures the IDs of the content structures by their external reference codes,
 *     without the ones that do not exist
 */
public record NavigationDto(
    @NotNull Long id,
    @NotNull Long siteId,
    @NotNull List<NavigationItemDto> navigationItems,
    @NotNull Map<String, Long> contentStructures)
    implements Serializable {
  public NavigationDto {
    navigationItems =
        navigationItems == null ? null : Collections.unmodifiableList(navigationItems);
    contentStructures =
        contentStructures == null ? null : Collections.unmodifiableMap(contentStructures);
  }
}
