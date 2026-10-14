/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.sitemap.model;

import java.util.Locale;

/** The languages of jod-ohjaaja-ui, in the order they are listed in the sitemap. */
public enum Language {
  FI("fi", Locale.of("fi", "FI")),
  SV("sv", Locale.of("sv", "SE")),
  EN("en", Locale.of("en", "US"));

  /** The language whose version x-default points to. */
  public static final Language DEFAULT = FI;

  private final String code;
  private final Locale locale;

  Language(String code, Locale locale) {
    this.code = code;
    this.locale = locale;
  }

  /** Language code used in UI URLs and hreflang, e.g. "fi". */
  public String code() {
    return code;
  }

  /** Key of the navigation's name_i18n map, e.g. "fi-FI". */
  public String languageTag() {
    return locale.toLanguageTag();
  }
}
