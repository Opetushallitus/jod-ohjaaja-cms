/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.auth.role;

public final class ContentProducerRole {

  /** Role name (key) and external reference code. */
  public static final String NAME = "jod-ohjaaja-cms-sisallontuottaja";

  public static final String EXTERNAL_REFERENCE_CODE = NAME;

  /** Name of the manually created site role that is taken over on existing environments. */
  public static final String LEGACY_NAME = "ContentProvider";

  public static final String TITLE_FI = "Sisällöntuottaja";

  public static final String TITLE_EN = "Content producer";

  public static final String DESCRIPTION_FI =
      "Roolin oikeudet määritellään ohjelmakoodissa (jod-ohjaaja-cms-auth), ja ne kirjoitetaan"
          + " yli aina, kun palvelin käynnistyy. Älä muuta tämän roolin oikeuksia"
          + " käyttöliittymässä.";

  public static final String DESCRIPTION_EN =
      "The permissions of this role are defined in code (jod-ohjaaja-cms-auth) and are"
          + " overwritten every time the server starts. Do not change the permissions of this"
          + " role in the user interface.";

  private ContentProducerRole() {}
}
