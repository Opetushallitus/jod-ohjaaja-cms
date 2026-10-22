/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.studyprogram.util;

import com.liferay.dynamic.data.mapping.model.LocalizedValue;
import com.liferay.portal.kernel.backgroundtask.BackgroundTask;
import com.liferay.portal.kernel.backgroundtask.constants.BackgroundTaskConstants;
import java.util.Locale;

public final class StudyProgramImporterUtil {
  private StudyProgramImporterUtil() {}

  public static LocalizedValue localized(String value, Locale locale) {
    var lv = new LocalizedValue(locale);
    lv.addString(locale, value);
    return lv;
  }

  public static boolean isActiveTask(BackgroundTask task) {
    if (task == null) {
      return false;
    }
    return task.getStatus() == BackgroundTaskConstants.STATUS_IN_PROGRESS
        || task.getStatus() == BackgroundTaskConstants.STATUS_NEW
        || task.getStatus() == BackgroundTaskConstants.STATUS_QUEUED;
  }
}
