/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.studyprogram.constants;

public final class OhjaajaPanelCategoryKeys {
  /**
   * The key must start with "site_administration." Liferay checks control panel access of the
   * portlets in this category against the site only for such categories (BaseControlPanelEntry);
   * otherwise site role permissions are ignored and only administrators see the apps.
   */
  public static final String OHJAAJA_PANEL_CATEGORY_KEY =
      "site_administration.fi_okm_jod_ohjaaja_cms_ohjaaja";

  private OhjaajaPanelCategoryKeys() {}
}
