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
  public static final Locale FINNISH = LocaleUtil.fromLanguageId("fi_FI");
  public static final Locale ENGLISH = LocaleUtil.fromLanguageId("en_US");
  public static final Locale SWEDISH = LocaleUtil.fromLanguageId("sv_SE");
  public static final String IMAGE_FOLDER_NAME = "koulutustarjonta-kuvat";
  public static final String IMAGE_FOLDER_DESCRIPTION = "Kuvat koulutustarjonnan artikkeleihin";
}
