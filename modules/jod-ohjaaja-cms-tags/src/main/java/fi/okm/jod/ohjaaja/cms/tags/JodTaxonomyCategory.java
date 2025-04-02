/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.tags;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;

public class JodTaxonomyCategory implements Serializable {
  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  protected JodCategoryType categoryType;

  @JsonIgnore
  public void setCategoryType(JodCategoryType categoryType) {
    this.categoryType = categoryType;
  }
}
