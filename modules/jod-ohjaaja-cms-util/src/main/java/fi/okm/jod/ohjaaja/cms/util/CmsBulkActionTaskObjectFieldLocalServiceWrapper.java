/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.util;

import com.liferay.object.model.ObjectField;
import com.liferay.object.model.ObjectFieldSetting;
import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.object.service.ObjectFieldLocalServiceWrapper;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.service.ServiceWrapper;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.Validator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Makes the upgrade of com.liferay.site.cms.site.initializer from 2.0.0 to 3.0.0 succeed in DXP
 * 2026.Q3 when the default locale is not en_US.
 *
 * <p>Its only step, CMSBulkActionTaskTaskResultUpgradeProcess, adds the field TASK_RESULT to the
 * object L_CMS_BULK_ACTION_TASK with an en_US label only, and adding a field requires a label in
 * the default locale (fi_FI): "ObjectFieldLabelException: Label is null for locale Finnish
 * (Finland)". This copies the en_US label to the default locale for that call only.
 *
 * <p>Our modules are deployed before the module upgrades run, so the wrapper is in place in time.
 * Remove when all environments have been upgraded.
 */
@Component(service = ServiceWrapper.class)
public class CmsBulkActionTaskObjectFieldLocalServiceWrapper
    extends ObjectFieldLocalServiceWrapper {

  private static final Log log =
      LogFactoryUtil.getLog(CmsBulkActionTaskObjectFieldLocalServiceWrapper.class);

  private static final String OBJECT_DEFINITION_EXTERNAL_REFERENCE_CODE = "L_CMS_BULK_ACTION_TASK";
  private static final String FIELD_EXTERNAL_REFERENCE_CODE = "TASK_RESULT";
  private static final String FIELD_NAME = "taskResult";

  @Reference private ObjectDefinitionLocalService objectDefinitionLocalService;

  public CmsBulkActionTaskObjectFieldLocalServiceWrapper() {
    super(null);
  }

  @Override
  public ObjectField addOrUpdateSystemObjectField(
      String externalReferenceCode,
      long userId,
      long listTypeDefinitionId,
      long objectDefinitionId,
      String businessType,
      String dbColumnName,
      String dbTableName,
      String dbType,
      boolean indexed,
      boolean indexedAsKeyword,
      String indexedLanguageId,
      Map<Locale, String> labelMap,
      boolean localized,
      String name,
      String readOnly,
      String readOnlyConditionExpression,
      boolean required,
      boolean state,
      List<ObjectFieldSetting> objectFieldSettings)
      throws PortalException {
    if (isTaskResultField(externalReferenceCode, name, objectDefinitionId)) {
      labelMap = withDefaultLocaleLabel(labelMap);
    }
    return super.addOrUpdateSystemObjectField(
        externalReferenceCode,
        userId,
        listTypeDefinitionId,
        objectDefinitionId,
        businessType,
        dbColumnName,
        dbTableName,
        dbType,
        indexed,
        indexedAsKeyword,
        indexedLanguageId,
        labelMap,
        localized,
        name,
        readOnly,
        readOnlyConditionExpression,
        required,
        state,
        objectFieldSettings);
  }

  private boolean isTaskResultField(
      String externalReferenceCode, String name, long objectDefinitionId) {
    if (!FIELD_EXTERNAL_REFERENCE_CODE.equals(externalReferenceCode) || !FIELD_NAME.equals(name)) {
      return false;
    }
    var objectDefinition = objectDefinitionLocalService.fetchObjectDefinition(objectDefinitionId);
    return objectDefinition != null
        && OBJECT_DEFINITION_EXTERNAL_REFERENCE_CODE.equals(
            objectDefinition.getExternalReferenceCode());
  }

  private static Map<Locale, String> withDefaultLocaleLabel(Map<Locale, String> labelMap) {
    var defaultLocale = LocaleUtil.getSiteDefault();
    if (labelMap == null
        || Validator.isNotNull(labelMap.get(defaultLocale))
        || Validator.isNull(labelMap.get(LocaleUtil.US))) {
      return labelMap;
    }
    var fixedLabelMap = new HashMap<>(labelMap);
    fixedLabelMap.put(defaultLocale, labelMap.get(LocaleUtil.US));
    log.info(
        "Added the "
            + defaultLocale
            + " label missing from field "
            + FIELD_EXTERNAL_REFERENCE_CODE
            + " of "
            + OBJECT_DEFINITION_EXTERNAL_REFERENCE_CODE
            + ", which would fail the upgrade of com.liferay.site.cms.site.initializer");
    return fixedLabelMap;
  }
}
