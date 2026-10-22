/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.studyprogram.dto;

import java.util.List;
import java.util.Map;

public record StudyProgramDto(
    String oid,
    Map<String, String> nimi,
    Map<String, String> kuvaus,
    String koulutustyyppi,
    String teemakuva,
    List<String> kielivalinta,
    List<KoulutusTiedot> koulutukset,
    List<ToteutusTiedot> toteutukset,
    List<Tutkintonimike> tutkintonimikkeet,
    Integer opintojenLaajuusNumero,
    Laajuusyksikko opintojenLaajuusyksikko) {

  public String getNimi(String language) {
    return getText(nimi, language);
  }

  public String getKuvaus(String language) {
    return getText(kuvaus, language);
  }

  /**
   * @return the text in the given language, or in Finnish if there is none, or an empty string if
   *     there is neither. A language can also be missing by having a null text.
   */
  private static String getText(Map<String, String> texts, String language) {
    if (texts == null) {
      return "";
    }
    var text = texts.get(language);
    if (text == null) {
      text = texts.get("fi");
    }
    return text == null ? "" : text;
  }

  public String getTeemakuvaUrl() {
    var url = teemakuva == null ? "" : teemakuva.strip();
    return url.isEmpty() || url.equals("null") ? null : url;
  }
}
