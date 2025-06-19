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

  public String getKuvaus(String language) {
    if (kuvaus == null || kuvaus.isEmpty()) {
      return "";
    }
    return kuvaus.getOrDefault(language, kuvaus.getOrDefault("fi", ""));
  }
}
