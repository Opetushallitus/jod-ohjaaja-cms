/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.studyprogram.importer.test;

import fi.okm.jod.ohjaaja.cms.studyprogram.client.KonfoClient;
import fi.okm.jod.ohjaaja.cms.studyprogram.dto.Laajuusyksikko;
import fi.okm.jod.ohjaaja.cms.studyprogram.dto.StudyProgramDto;
import java.util.List;
import java.util.Map;

/**
 * Mock implementation of KonfoClient for testing purposes. Returns predefined test data instead of
 * making actual HTTP calls. Registered manually as OSGi service in test setup with high service
 * ranking.
 */
public class MockKonfoClient extends KonfoClient {

  private volatile String program2KuvausSuffix = "";
  private volatile String program2Teemakuva;

  /** Appends the given text to the Finnish description of the second study program. */
  public void setProgram2KuvausSuffix(String program2KuvausSuffix) {
    this.program2KuvausSuffix = program2KuvausSuffix;
  }

  /** Sets the image URL of the second study program, which has none by default. */
  public void setProgram2Teemakuva(String program2Teemakuva) {
    this.program2Teemakuva = program2Teemakuva;
  }

  @Override
  public List<StudyProgramDto> fetchStudyPrograms() {
    return createMockStudyPrograms(program2KuvausSuffix, program2Teemakuva);
  }

  private static List<StudyProgramDto> createMockStudyPrograms(
      String program2KuvausSuffix, String program2Teemakuva) {
    StudyProgramDto program1 =
        new StudyProgramDto(
            "1.2.246.562.20.00000000001",
            Map.of(
                "fi", "Tietojenkäsittelytieteen kandidaattiohjelma",
                "en", "Bachelor's Programme in Computer Science",
                "sv", "Kandidatprogram i datavetenskap"),
            Map.of(
                "fi", "Opiskele tietojenkäsittelytiedettä ja ohjelmointia.",
                "en", "Study computer science and programming.",
                "sv", "Studera datavetenskap och programmering."),
            "amk",
            "https://example.com/teemakuva1.jpg",
            List.of("fi", "en", "sv"),
            List.of(),
            List.of(),
            List.of(),
            180,
            new Laajuusyksikko(
                "opintojenlaajuusyksikko_2", Map.of("fi", "opintopiste", "en", "ECTS credit")));

    StudyProgramDto program2 =
        new StudyProgramDto(
            "1.2.246.562.20.00000000002",
            Map.of(
                "fi", "Liiketalouden maisteriohjelma",
                "en", "Master's Programme in Business Administration"),
            Map.of(
                "fi",
                "Kehitä liiketoimintaosaamistasi maisteriohjelmassa." + program2KuvausSuffix,
                "en",
                "Develop your business expertise in master's programme."),
            "yo",
            program2Teemakuva,
            List.of("fi", "en"),
            List.of(),
            List.of(),
            List.of(),
            120,
            new Laajuusyksikko("opintojenlaajuusyksikko_2", Map.of("fi", "opintopiste")));

    StudyProgramDto program3 =
        new StudyProgramDto(
            "1.2.246.562.20.00000000003",
            Map.of("fi", "Minimaaliohjelma"),
            Map.of(),
            "amk",
            null,
            List.of("fi"),
            List.of(),
            List.of(),
            List.of(),
            null,
            null);

    // The image of this study program can never be downloaded
    StudyProgramDto program4 =
        new StudyProgramDto(
            "1.2.246.562.20.00000000004",
            Map.of("fi", "Ohjelma ilman saatavilla olevaa kuvaa"),
            Map.of("fi", "Kuvan lataus epäonnistuu aina."),
            "amk",
            "http://127.0.0.1:9/teemakuva4.jpg",
            List.of("fi"),
            List.of(),
            List.of(),
            List.of(),
            null,
            null);

    return List.of(program1, program2, program3, program4);
  }
}
