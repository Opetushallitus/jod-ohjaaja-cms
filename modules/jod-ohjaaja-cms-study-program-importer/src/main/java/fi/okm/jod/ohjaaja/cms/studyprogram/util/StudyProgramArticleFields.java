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

import com.liferay.dynamic.data.mapping.model.DDMForm;
import com.liferay.dynamic.data.mapping.model.LocalizedValue;
import com.liferay.dynamic.data.mapping.model.UnlocalizedValue;
import com.liferay.dynamic.data.mapping.storage.DDMFormFieldValue;
import com.liferay.dynamic.data.mapping.storage.DDMFormValues;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.json.JSONUtil;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.util.StringUtil;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Places the {@link StudyProgramArticleValues} in the fields of the study program structure. The
 * {@link StudyProgramFingerprint} of the values does not cover how they are placed, so increase
 * {@link StudyProgramFingerprint#VERSION} whenever changing what this class produces. Keep all such
 * code here, so that the rule has a single place to apply to.
 */
public final class StudyProgramArticleFields {

  /** The name of the field that has the image of the article. */
  public static final String IMAGE_FIELD_NAME = "image";

  private StudyProgramArticleFields() {}

  /**
   * @param image the image of the article, or null if it has none
   */
  public static DDMFormValues createFormValues(
      DDMForm ddmForm, StudyProgramArticleValues values, FileEntry image) {
    var formValues = new DDMFormValues(ddmForm);
    formValues.setAvailableLocales(Set.of(FINNISH, ENGLISH, SWEDISH));
    formValues.setDefaultLocale(FINNISH);

    formValues.addDDMFormFieldValue(createFieldValue("ingress", values.ingress()));
    if (image != null) {
      formValues.addDDMFormFieldValue(createImageFieldValue(image, values.title()));
    } else {
      formValues.addDDMFormFieldValue(createUnlocalizedFieldValue(IMAGE_FIELD_NAME, ""));
    }
    formValues.addDDMFormFieldValue(createFieldValue("content", values.content()));
    formValues.addDDMFormFieldValue(createLinkFieldsetValue(values, formValues.getDefaultLocale()));
    return formValues;
  }

  private static DDMFormFieldValue createUnlocalizedFieldValue(String fieldName, String value) {
    var fieldValue = new DDMFormFieldValue();
    fieldValue.setName(fieldName);
    fieldValue.setFieldReference(fieldName);
    fieldValue.setInstanceId(StringUtil.randomString());
    fieldValue.setValue(new UnlocalizedValue(value));
    return fieldValue;
  }

  private static DDMFormFieldValue createFieldValue(String fieldName, Map<Locale, String> values) {
    var fieldValue = new DDMFormFieldValue();
    fieldValue.setName(fieldName);
    fieldValue.setFieldReference(fieldName);
    fieldValue.setInstanceId(StringUtil.randomString());

    var localizedValue = new LocalizedValue(FINNISH);
    values.forEach(localizedValue::addString);
    fieldValue.setValue(localizedValue);

    return fieldValue;
  }

  private static DDMFormFieldValue createImageFieldValue(
      FileEntry fileEntry, Map<Locale, String> titleMap) {

    var fieldValue = new DDMFormFieldValue();
    fieldValue.setName(IMAGE_FIELD_NAME);
    fieldValue.setFieldReference(IMAGE_FIELD_NAME);
    fieldValue.setInstanceId(StringUtil.randomString());

    var localizedValue = new LocalizedValue(FINNISH);
    for (Map.Entry<Locale, String> entry : titleMap.entrySet()) {
      localizedValue.addString(
          entry.getKey(),
          JSONUtil.put("alt", entry.getValue())
              .put("groupId", fileEntry.getGroupId())
              .put("name", fileEntry.getFileName())
              .put("title", entry.getValue())
              .put("type", "document")
              .put("uuid", fileEntry.getUuid())
              .toString());
    }

    fieldValue.setValue(localizedValue);

    return fieldValue;
  }

  private static DDMFormFieldValue createLinkFieldsetValue(
      StudyProgramArticleValues values, Locale defaultLocale) {

    var fieldset = new DDMFormFieldValue();
    fieldset.setName("FieldsetLink");
    fieldset.setValue(new UnlocalizedValue(StringPool.BLANK));

    var linkText = new DDMFormFieldValue();
    linkText.setName("linktext");
    var textVal = new LocalizedValue(defaultLocale);
    values.title().forEach(textVal::addString);
    linkText.setValue(textVal);

    var linkUrl = new DDMFormFieldValue();
    linkUrl.setName("linkurl");
    var urlVal = new LocalizedValue(defaultLocale);
    values.linkUrl().forEach(urlVal::addString);
    linkUrl.setValue(urlVal);

    fieldset.addNestedDDMFormFieldValue(linkText);
    fieldset.addNestedDDMFormFieldValue(linkUrl);

    return fieldset;
  }
}
