/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.util;

/**
 * The type of a web content, told by its structure. The numeric structure ids differ between the
 * environments, so the type is the one thing the other services can rely on.
 */
public enum ContentType {
  ARTICLE,
  NEWS;

  /** The type of a web content whose structure has the given external reference code. */
  public static ContentType fromStructureExternalReferenceCode(String externalReferenceCode) {
    return StructureInitializer.NEWS_STRUCTURE_EXTERNAL_REFERENCE_CODE.equals(externalReferenceCode)
        ? NEWS
        : ARTICLE;
  }
}
