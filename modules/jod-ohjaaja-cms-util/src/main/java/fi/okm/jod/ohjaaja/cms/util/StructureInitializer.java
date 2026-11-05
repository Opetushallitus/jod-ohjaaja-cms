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
 * Manages the web content structures of the JOD Ohjaaja site in code. The definitions are in {@code
 * src/main/resources/fi/okm/jod/ohjaaja/cms/util/structures} and are the source of truth: changes
 * made to these structures in the UI are overwritten on startup.
 */
public interface StructureInitializer {

  /** The structure of the articles. */
  String ARTICLE_STRUCTURE_EXTERNAL_REFERENCE_CODE = "jod-ohjaaja-cms-article";

  /** The link fieldset of the article structure. */
  String LINK_FIELDSET_EXTERNAL_REFERENCE_CODE = "jod-ohjaaja-cms-link-fieldset";

  /**
   * Creates the missing structures and updates the ones that differ from their definition. A
   * structure created by hand before it was managed in code is taken over by its former external
   * reference code or, for the article structure, by its name. A structure is not updated if the
   * definition would remove or change the type of a field, as the content of the existing articles
   * would be lost.
   */
  void initStructures();
}
