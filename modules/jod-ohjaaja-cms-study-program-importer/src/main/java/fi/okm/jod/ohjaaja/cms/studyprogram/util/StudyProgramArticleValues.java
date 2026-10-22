/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.studyprogram.util;

import static fi.okm.jod.ohjaaja.cms.studyprogram.constants.StudyProgramImporterConstants.ENGLISH;
import static fi.okm.jod.ohjaaja.cms.studyprogram.constants.StudyProgramImporterConstants.FINNISH;
import static fi.okm.jod.ohjaaja.cms.studyprogram.constants.StudyProgramImporterConstants.SWEDISH;

import com.liferay.portal.kernel.util.HtmlUtil;
import fi.okm.jod.ohjaaja.cms.studyprogram.dto.StudyProgramDto;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * The values that a study program article is built from. The article is built from these values
 * only, and the {@link StudyProgramFingerprint} covers all of them, so a change in how they are
 * derived from the study program data updates the articles on the next import. A new value shown in
 * the article is added here instead of being read from the study program data when building the
 * article.
 *
 * @param title the title of the article, also used for the image and the link text
 * @param linkUrl the URL of the study program in Konfo
 * @param imageUrl the URL of the image, or null if the article uses the placeholder image
 */
public record StudyProgramArticleValues(
    String oid,
    Map<Locale, String> title,
    Map<Locale, String> ingress,
    Map<Locale, String> content,
    Map<Locale, String> linkUrl,
    String imageUrl) {

  /**
   * Derives the values of the article from the study program data.
   *
   * @param konfoUrl the URL of the Konfo that the article links to
   */
  public static StudyProgramArticleValues of(StudyProgramDto studyProgram, String konfoUrl) {
    return new StudyProgramArticleValues(
        studyProgram.oid(),
        byLanguage(studyProgram::getNimi),
        byLanguage(
            language ->
                StudyProgramIngress.extract(HtmlUtil.stripHtml(studyProgram.getKuvaus(language)))),
        byLanguage(studyProgram::getKuvaus),
        byLanguage(language -> konfoUrl + "/" + language + "/koulutus/" + studyProgram.oid()),
        studyProgram.getTeemakuvaUrl());
  }

  private static Map<Locale, String> byLanguage(UnaryOperator<String> valueOfLanguage) {
    return Map.of(
        FINNISH, valueOfLanguage.apply("fi"),
        ENGLISH, valueOfLanguage.apply("en"),
        SWEDISH, valueOfLanguage.apply("sv"));
  }
}
