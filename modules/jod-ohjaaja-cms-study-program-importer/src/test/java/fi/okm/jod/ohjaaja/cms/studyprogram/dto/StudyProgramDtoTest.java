/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.studyprogram.dto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

/** Unit tests for the values that a study program article falls back to. */
public class StudyProgramDtoTest {

  @Test
  public void testMissingLanguageFallsBackToFinnish() {
    var studyProgram =
        studyProgram(
            Map.of("fi", "Koulutus", "sv", "Utbildning"),
            Map.of("fi", "Kuvaus.", "sv", "Beskrivning."),
            null);

    Assert.assertEquals("Utbildning", studyProgram.getNimi("sv"));
    Assert.assertEquals("Koulutus", studyProgram.getNimi("en"));
    Assert.assertEquals("Beskrivning.", studyProgram.getKuvaus("sv"));
    Assert.assertEquals("Kuvaus.", studyProgram.getKuvaus("en"));
  }

  @Test
  public void testNullLanguageFallsBackToFinnish() {
    var nimi = new HashMap<String, String>();
    nimi.put("fi", "Koulutus");
    nimi.put("sv", null);
    var kuvaus = new HashMap<String, String>();
    kuvaus.put("fi", null);
    kuvaus.put("sv", null);
    var studyProgram = studyProgram(nimi, kuvaus, null);

    Assert.assertEquals("Koulutus", studyProgram.getNimi("sv"));
    Assert.assertEquals("", studyProgram.getKuvaus("sv"));
    Assert.assertEquals("", studyProgram.getKuvaus("en"));
  }

  @Test
  public void testMissingKuvausIsEmpty() {
    Assert.assertEquals("", studyProgram(Map.of("fi", "Koulutus"), null, null).getKuvaus("fi"));
    Assert.assertEquals("", studyProgram(Map.of("fi", "Koulutus"), Map.of(), null).getKuvaus("fi"));
  }

  @Test
  public void testBlankTeemakuvaIsMissing() {
    Assert.assertNull(studyProgram(null).getTeemakuvaUrl());
    Assert.assertNull(studyProgram("").getTeemakuvaUrl());
    Assert.assertNull(studyProgram(" ").getTeemakuvaUrl());
    Assert.assertNull(studyProgram("null").getTeemakuvaUrl());
    Assert.assertEquals(
        "https://example.com/a.jpg", studyProgram("https://example.com/a.jpg").getTeemakuvaUrl());
    Assert.assertEquals(
        "https://example.com/a.jpg",
        studyProgram(" https://example.com/a.jpg\n").getTeemakuvaUrl());
  }

  private static StudyProgramDto studyProgram(String teemakuva) {
    return studyProgram(Map.of("fi", "Koulutus"), Map.of("fi", "Kuvaus."), teemakuva);
  }

  private static StudyProgramDto studyProgram(
      Map<String, String> nimi, Map<String, String> kuvaus, String teemakuva) {
    return new StudyProgramDto(
        "oid",
        nimi,
        kuvaus,
        "amk",
        teemakuva,
        List.of("fi"),
        List.of(),
        List.of(),
        List.of(),
        null,
        null);
  }
}
