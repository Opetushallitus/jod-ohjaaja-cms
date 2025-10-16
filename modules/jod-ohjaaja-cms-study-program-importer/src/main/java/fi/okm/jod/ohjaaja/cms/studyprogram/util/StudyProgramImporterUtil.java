/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.studyprogram.util;

import static fi.okm.jod.ohjaaja.cms.studyprogram.constants.StudyProgramImporterConstants.FINNISH;

import com.liferay.dynamic.data.mapping.model.LocalizedValue;
import com.liferay.dynamic.data.mapping.model.UnlocalizedValue;
import com.liferay.dynamic.data.mapping.storage.DDMFormFieldValue;
import com.liferay.portal.kernel.backgroundtask.BackgroundTask;
import com.liferay.portal.kernel.backgroundtask.constants.BackgroundTaskConstants;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.model.role.RoleConstants;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.service.RoleLocalServiceUtil;
import com.liferay.portal.kernel.service.UserLocalServiceUtil;
import com.liferay.portal.kernel.util.StringUtil;
import java.util.Locale;
import java.util.Map;

public class StudyProgramImporterUtil {
  public static LocalizedValue localized(String value, Locale locale) {
    var lv = new LocalizedValue(locale);
    lv.addString(locale, value);
    return lv;
  }

  public static DDMFormFieldValue createUnlocalizedFieldValue(String fieldName, String value) {
    var fieldValue = new DDMFormFieldValue();
    fieldValue.setName(fieldName);
    fieldValue.setFieldReference(fieldName);
    fieldValue.setInstanceId(StringUtil.randomString());
    fieldValue.setValue(new UnlocalizedValue(value));
    return fieldValue;
  }

  public static DDMFormFieldValue createFieldValue(String fieldName, Map<Locale, String> values) {
    var fieldValue = new DDMFormFieldValue();
    fieldValue.setName(fieldName);
    fieldValue.setFieldReference(fieldName);
    fieldValue.setInstanceId(StringUtil.randomString());

    var localizedValue = new LocalizedValue(FINNISH);
    for (Map.Entry<Locale, String> entry : values.entrySet()) {
      if (entry.getValue() != null) {
        localizedValue.addString(entry.getKey(), entry.getValue());
      } else {
        localizedValue.addString(entry.getKey(), "");
      }
    }

    fieldValue.setValue(localizedValue);

    return fieldValue;
  }

  public static DDMFormFieldValue createImageFieldValue(
      FileEntry fileEntry, Map<Locale, String> titleMap) {

    var fieldValue = new DDMFormFieldValue();
    fieldValue.setName("image");
    fieldValue.setFieldReference("image");
    fieldValue.setInstanceId(StringUtil.randomString());

    var localizedValue = new LocalizedValue(FINNISH);
    for (Map.Entry<Locale, String> entry : titleMap.entrySet()) {

      localizedValue.addString(
          entry.getKey(),
          String.format(
              """
            {
              "alt": "%s",
              "groupId": %d,
              "name": "%s",
              "title": "%s",
              "type": "document",
              "uuid": "%s"
            }
          """,
              entry.getValue(),
              fileEntry.getGroupId(),
              fileEntry.getFileName(),
              entry.getValue(),
              fileEntry.getUuid()));
    }

    fieldValue.setValue(localizedValue);

    return fieldValue;
  }

  public static User getUser(long companyId) throws PortalException {
    var role = RoleLocalServiceUtil.fetchRole(companyId, RoleConstants.ADMINISTRATOR);

    if (role == null) {
      return UserLocalServiceUtil.getGuestUser(companyId);
    }

    var adminUsers = UserLocalServiceUtil.getRoleUsers(role.getRoleId(), 0, 1);

    if (adminUsers.isEmpty()) {
      return UserLocalServiceUtil.getGuestUser(companyId);
    }

    return adminUsers.getFirst();
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
