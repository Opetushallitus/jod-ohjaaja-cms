/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.studyprogram.service;

import static fi.okm.jod.ohjaaja.cms.studyprogram.constants.StudyProgramImporterConstants.EXTERNAL_REFERENCE_CODE;
import static fi.okm.jod.ohjaaja.cms.studyprogram.constants.StudyProgramImporterConstants.JOD_GROUP_ID;
import static fi.okm.jod.ohjaaja.cms.studyprogram.util.StudyProgramImporterUtil.getUser;
import static fi.okm.jod.ohjaaja.cms.studyprogram.util.StudyProgramImporterUtil.localized;

import com.liferay.dynamic.data.mapping.constants.DDMStructureConstants;
import com.liferay.dynamic.data.mapping.model.*;
import com.liferay.dynamic.data.mapping.service.DDMStructureLayoutLocalService;
import com.liferay.dynamic.data.mapping.service.DDMStructureLocalService;
import com.liferay.dynamic.data.mapping.storage.StorageType;
import com.liferay.journal.model.JournalArticle;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.PortalUtil;
import fi.okm.jod.ohjaaja.cms.studyprogram.service.exception.StudyProgramDDMStructureDeleteException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = StudyProgramStructureService.class)
public class StudyProgramStructureService {

  private static final Log log = LogFactoryUtil.getLog(StudyProgramStructureService.class);

  @Reference private DDMStructureLocalService ddmStructureLocalService;
  @Reference private DDMStructureLayoutLocalService ddmStructureLayoutLocalService;

  public DDMStructure getOrCreateDDMStructure(boolean createIfNotExists) {
    try {
      var classNameId = PortalUtil.getClassNameId(JournalArticle.class);

      var structure =
          ddmStructureLocalService.fetchStructureByExternalReferenceCode(
              EXTERNAL_REFERENCE_CODE, JOD_GROUP_ID, classNameId);

      if (structure == null && createIfNotExists) {
        structure = createDDMStructure(classNameId);
        log.info(
            "Created new koulutustarjonta structure with external reference code: "
                + EXTERNAL_REFERENCE_CODE
                + ", structure ID: "
                + structure.getStructureId());
      } else {
        log.info(
            "Found existing koulutustarjonta structure with external reference code: "
                + EXTERNAL_REFERENCE_CODE);
      }
      return structure;
    } catch (PortalException e) {
      log.error("Failed to create or fetch DDM structure", e);
      return null;
    }
  }

  public void deleteDDMStructure() throws StudyProgramDDMStructureDeleteException {
    var structure = getOrCreateDDMStructure(false);
    if (structure == null) {
      log.warn(
          "No DDM structure found to delete with external reference code: "
              + EXTERNAL_REFERENCE_CODE);
      return;
    }
    try {
      var structureLayout =
          ddmStructureLayoutLocalService.fetchStructureLayout(
              JOD_GROUP_ID, structure.getClassNameId(), structure.getStructureKey());
      if (structureLayout != null) {
        ddmStructureLayoutLocalService.deleteDDMStructureLayout(structureLayout);
        log.info("Deleted DDM structure layout for: " + EXTERNAL_REFERENCE_CODE);
      }
      ddmStructureLocalService.deleteStructure(structure);
      log.info("Deleted DDM structure: " + EXTERNAL_REFERENCE_CODE);
    } catch (PortalException e) {
      log.error("Failed to delete DDM structure: " + EXTERNAL_REFERENCE_CODE, e);
      throw new StudyProgramDDMStructureDeleteException(
          "Failed to delete DDM structure: " + EXTERNAL_REFERENCE_CODE);
    }
  }

  private DDMStructure createDDMStructure(long classNameId) throws PortalException {

    var user = getUser(PortalUtil.getDefaultCompanyId());
    var defaultLocale = LocaleUtil.fromLanguageId("fi_FI");
    var nameMap =
        Map.of(
            defaultLocale,
            "Koulutustarjonta",
            LocaleUtil.fromLanguageId("en_US"),
            "Study Program",
            LocaleUtil.fromLanguageId("sv_SE"),
            "Utbildningserbjudande");
    var descriptionMap =
        Map.of(
            defaultLocale,
            "Ohjaaja CMS:n koulutustarjontarakenteet",
            LocaleUtil.fromLanguageId("en_US"),
            "Study program structure for Ohjaaja CMS",
            LocaleUtil.fromLanguageId("sv_SE"),
            "Struktur för utbildningserbjudande i Ohjaaja CMS");

    var serviceContext = new ServiceContext();
    serviceContext.setAddGroupPermissions(true);
    serviceContext.setAddGuestPermissions(true);
    serviceContext.setCompanyId(PortalUtil.getDefaultCompanyId());
    serviceContext.setScopeGroupId(JOD_GROUP_ID);
    serviceContext.setUserId(user.getUserId());

    var layout = new DDMFormLayout();

    layout.setDefaultLocale(defaultLocale);
    layout.setPaginationMode(DDMFormLayout.SINGLE_PAGE_MODE);

    var page = new DDMFormLayoutPage();
    page.setTitle(localized("Koulutustarjonta", defaultLocale));

    var row1 = new DDMFormLayoutRow();
    var row2 = new DDMFormLayoutRow();
    var row3 = new DDMFormLayoutRow();

    row1.addDDMFormLayoutColumn(new DDMFormLayoutColumn(12, "StudyProgramIngress"));
    row2.addDDMFormLayoutColumn(new DDMFormLayoutColumn(12, "StudyProgramContent"));
    row3.addDDMFormLayoutColumn(new DDMFormLayoutColumn(12, "StudyProgramImage"));

    page.addDDMFormLayoutRow(row1);
    page.addDDMFormLayoutRow(row2);
    page.addDDMFormLayoutRow(row3);

    layout.addDDMFormLayoutPage(page);

    var structure =
        ddmStructureLocalService.addStructure(
            EXTERNAL_REFERENCE_CODE,
            user.getUserId(),
            JOD_GROUP_ID,
            0,
            classNameId,
            EXTERNAL_REFERENCE_CODE,
            nameMap,
            descriptionMap,
            buildDDMForm(defaultLocale),
            layout,
            StorageType.DEFAULT.toString(),
            DDMStructureConstants.TYPE_DEFAULT,
            serviceContext);

    return structure;
  }

  private DDMForm buildDDMForm(Locale locale) {
    var form = new DDMForm();
    form.setAvailableLocales(Set.of(locale));
    form.setDefaultLocale(locale);

    form.addDDMFormField(
        createField("StudyProgramIngress", "text", "string", false, "Ingressi", locale));
    form.addDDMFormField(createField("StudyProgramImage", "image", "image", false, "Kuva", locale));
    form.addDDMFormField(
        createField("StudyProgramContent", "rich_text", "html", true, "Sisältö", locale));

    return form;
  }

  private DDMFormField createField(
      String name, String type, String dataType, boolean required, String label, Locale locale) {
    var field = new DDMFormField(name, type);
    field.setRequired(required);
    field.setLabel(localized(label, locale));
    field.setLocalizable(!type.equals("image"));
    field.setDataType(dataType);
    field.setIndexType(!type.equals("image") ? "text" : "none");
    field.setShowLabel(true);
    field.setRepeatable(false);
    return field;
  }
}
