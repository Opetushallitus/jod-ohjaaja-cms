/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.studyprogram.constants;

import com.liferay.portal.kernel.util.LocaleUtil;
import java.util.Locale;

public class StudyProgramImporterConstants {
  public static final String EXTERNAL_REFERENCE_CODE = "ohjaaja-study-program";
  public static final long JOD_GROUP_ID = 20117;
  public static final String STUDY_PROGRAM_TAG_CATEGORY_EXTERNAL_REFERENCE_CODE =
      "ohjaaja-study-program-tag-category";
  public static final String STUDY_PROGRAM_TAG_CATEGORY_FINNISH_TITLE = "Koulutus";
  public static final String STUDY_PROGRAM_TAG_CATEGORY_SWEDISH_TITLE = "Utbildning";
  public static final String STUDY_PROGRAM_TAG_CATEGORY_ENGLISH_TITLE = "Education";
  public static final String STUDY_PROGRAM_CATEGORY_VOCABULARY_EXTERNAL_REFERENCE_CODE =
      "ohjaaja-study-program-category-vocabulary";
  public static final String STUDY_PROGRAM_CATEGORY_VOCABULARY_FINNISH_TITLE = "Kategoria";
  public static final String STUDY_PROGRAM_CATEGORY_VOCABULARY_SWEDISH_TITLE = "Kategori";
  public static final String STUDY_PROGRAM_CATEGORY_VOCABULARY_ENGLISH_TITLE = "Category";
  public static final Locale FINNISH = LocaleUtil.fromLanguageId("fi_FI");
  public static final Locale ENGLISH = LocaleUtil.fromLanguageId("en_US");
  public static final Locale SWEDISH = LocaleUtil.fromLanguageId("sv_SE");
  public static final String IMAGE_FOLDER_NAME = "koulutustarjonta-kuvat";
  public static final String IMAGE_FOLDER_DESCRIPTION = "Kuvat koulutustarjonnan artikkeleihin";
  public static final String STUDY_PROGRAM_PARENT_CATEGORY_EXTERNAL_REFERENCE_CODE =
      "ohjaaja-study-program-parent-category";
  public static final String STUDY_PROGRAM_PARENT_CATEGORY_FINNISH_TITLE =
      "Ammatillinen kehittyminen";
  public static final String STUDY_PROGRAM_PARENT_CATEGORY_SWEDISH_TITLE =
      "Professionell utveckling";
  public static final String STUDY_PROGRAM_PARENT_CATEGORY_ENGLISH_TITLE =
      "Professional development";
  public static final String STUDY_PROGRAM_CATEGORY_EXTERNAL_REFERENCE_CODE =
      "ohjaaja-study-program-category";
  public static final String STUDY_PROGRAM_CATEGORY_FINNISH_TITLE = "Ohjausalan koulutukset";
  public static final String STUDY_PROGRAM_CATEGORY_SWEDISH_TITLE = "Utbildningar inom vägledning";
  public static final String STUDY_PROGRAM_CATEGORY_ENGLISH_TITLE = "Guidance field training";
  public static final String STUDY_PROGRAM_PLACEHOLDER_IMAGE_FILE_NAME =
      "study_program_image_placeholder.png";
  public static final String STUDY_PROGRAM_PLACEHOLDER_IMAGE_PATH =
      "/images/" + STUDY_PROGRAM_PLACEHOLDER_IMAGE_FILE_NAME;
  public static final String STUDY_PROGRAM_PLACEHOLDER_IMAGE_ERC =
      "study_program_image_placeholder";
}
