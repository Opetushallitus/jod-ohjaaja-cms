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

import com.liferay.data.engine.service.DEDataDefinitionFieldLinkLocalService;
import com.liferay.dynamic.data.mapping.constants.DDMStructureConstants;
import com.liferay.dynamic.data.mapping.form.field.type.constants.DDMFormFieldTypeConstants;
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
import com.liferay.portal.kernel.util.Validator;
import fi.okm.jod.ohjaaja.cms.studyprogram.service.exception.StudyProgramDDMStructureDeleteException;
import fi.okm.jod.ohjaaja.cms.studyprogram.util.StudyProgramImporterUtil;
import java.util.*;
import java.util.stream.Stream;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = StudyProgramStructureService.class)
public class StudyProgramStructureService {

  private static final Log log = LogFactoryUtil.getLog(StudyProgramStructureService.class);

  private static final String FIELDSET_KEY = "LINK-FIELD-SET";
  private static final String FIELDSET_FIELD_NAME = "FieldsetLink";

  @Reference private DDMStructureLocalService ddmStructureLocalService;
  @Reference private DDMStructureLayoutLocalService ddmStructureLayoutLocalService;
  @Reference private DEDataDefinitionFieldLinkLocalService deDataDefinitionFieldLinkLocalService;

  public DDMStructure getOrCreateDDMStructure(boolean createIfNotExists) {
    try {
      var classNameId = PortalUtil.getClassNameId(JournalArticle.class);

      var structure =
          ddmStructureLocalService.fetchStructureByExternalReferenceCode(
              EXTERNAL_REFERENCE_CODE, JOD_GROUP_ID, classNameId);

      if (structure == null && createIfNotExists) {
        structure = createDDMStructure();
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
      long jaClassNameId = PortalUtil.getClassNameId(JournalArticle.class.getName());

      deDataDefinitionFieldLinkLocalService.deleteDEDataDefinitionFieldLinks(
          jaClassNameId, structure.getStructureId());

      unlinkFieldsetFromStructure(structure);

      deleteLayoutIfExists(
          structure.getGroupId(), structure.getClassNameId(), structure.getStructureKey());

      ddmStructureLocalService.deleteStructure(structure);
      log.info("Deleted DDM structure: " + EXTERNAL_REFERENCE_CODE);

      DDMStructure fieldSet =
          ddmStructureLocalService.fetchStructure(JOD_GROUP_ID, jaClassNameId, FIELDSET_KEY);

      if (fieldSet != null) {
        deDataDefinitionFieldLinkLocalService.deleteDEDataDefinitionFieldLinks(
            jaClassNameId,
            structure.getStructureId()); // varmistus; ei haittaa vaikka ei olisi rivejä

        deleteLayoutIfExists(
            fieldSet.getGroupId(), fieldSet.getClassNameId(), fieldSet.getStructureKey());

        ddmStructureLocalService.deleteStructure(fieldSet);
        log.info("Deleted field set: " + FIELDSET_KEY);
      }
    } catch (PortalException e) {
      log.error("Failed to delete DDM structure: " + EXTERNAL_REFERENCE_CODE, e);
      throw new StudyProgramDDMStructureDeleteException(
          "Failed to delete DDM structure: " + EXTERNAL_REFERENCE_CODE);
    }
  }

  private void unlinkFieldsetFromStructure(DDMStructure main) throws PortalException {

    var form = main.getDDMForm();
    var kept = new ArrayList<DDMFormField>();
    for (var f : form.getDDMFormFields()) {
      if (!FIELDSET_FIELD_NAME.equals(f.getName())) {
        kept.add(f);
      }
    }
    form.setDDMFormFields(kept);

    var oldLayout = main.getDDMFormLayout();
    var newLayout = new DDMFormLayout();
    newLayout.setDefaultLocale(oldLayout.getDefaultLocale());
    newLayout.setPaginationMode(oldLayout.getPaginationMode());

    var newPages =
        oldLayout.getDDMFormLayoutPages().stream()
            .flatMap(
                page -> {
                  var newRows =
                      page.getDDMFormLayoutRows().stream()
                          .flatMap(
                              row -> {
                                var newCols =
                                    row.getDDMFormLayoutColumns().stream()
                                        .flatMap(
                                            col -> {
                                              var keptNames =
                                                  col.getDDMFormFieldNames().stream()
                                                      .filter(
                                                          name -> !FIELDSET_FIELD_NAME.equals(name))
                                                      .toList();
                                              if (keptNames.isEmpty()) return Stream.empty();
                                              return Stream.of(
                                                  new DDMFormLayoutColumn(
                                                      col.getSize(),
                                                      keptNames.toArray(new String[0])));
                                            })
                                        .toList();
                                if (newCols.isEmpty()) return Stream.empty();
                                var newRow = new DDMFormLayoutRow();
                                newRow.setDDMFormLayoutColumns(newCols);
                                return Stream.of(newRow);
                              })
                          .toList();
                  if (newRows.isEmpty()) return null;
                  var newPage = new DDMFormLayoutPage();
                  newPage.setTitle(page.getTitle());
                  newPage.setDescription(page.getDescription());
                  newPage.setDDMFormLayoutRows(newRows);
                  return Stream.of(newPage);
                })
            .filter(Objects::nonNull)
            .toList();
    newLayout.setDDMFormLayoutPages(newPages);

    var sc = new ServiceContext();
    sc.setAddGroupPermissions(true);
    sc.setAddGuestPermissions(true);
    sc.setScopeGroupId(main.getGroupId());
    sc.setUserId(main.getUserId());

    ddmStructureLocalService.updateStructure(
        main.getUserId(),
        main.getGroupId(),
        main.getParentStructureId(),
        main.getClassNameId(),
        main.getStructureKey(),
        main.getNameMap(),
        main.getDescriptionMap(),
        form,
        newLayout,
        sc);
  }

  private void deleteLayoutIfExists(long groupId, long classNameId, String structureKey)
      throws PortalException {
    var layout =
        ddmStructureLayoutLocalService.fetchStructureLayout(groupId, classNameId, structureKey);
    if (layout != null) {
      ddmStructureLayoutLocalService.deleteDDMStructureLayout(layout);
    }
  }

  public DDMStructure createDDMStructure() throws PortalException {

    var user = getUser(PortalUtil.getDefaultCompanyId());
    var fi = LocaleUtil.fromLanguageId("fi_FI");
    var sv = LocaleUtil.fromLanguageId("sv_SE");
    var en = LocaleUtil.fromLanguageId("en_US");

    var linkFieldSet = addLinkFieldSet(user.getUserId(), fi, sv, en);

    var mainStructure = addMainStructure(user.getUserId(), fi, sv, en, linkFieldSet);

    deDataDefinitionFieldLinkLocalService.addDEDataDefinitionFieldLink(
        JOD_GROUP_ID,
        PortalUtil.getClassNameId(JournalArticle.class.getName()),
        mainStructure.getStructureId(),
        linkFieldSet.getStructureId(),
        FIELDSET_FIELD_NAME);

    return mainStructure;
  }

  private DDMStructure addLinkFieldSet(long userId, Locale fi, Locale sv, Locale en)
      throws PortalException {

    var ddmForm = new DDMForm();
    ddmForm.setAvailableLocales(new HashSet<>(Arrays.asList(fi, sv, en)));
    ddmForm.setDefaultLocale(fi);

    var linkText = new DDMFormField("linktext", DDMFormFieldTypeConstants.TEXT);
    linkText.setLocalizable(true);
    linkText.setRequired(false);
    linkText.setIndexType("keyword");
    linkText.setDataType("string");
    linkText.setLabel(lv(fi, sv, en, "Kuvaus", "Text", "Text"));
    linkText.setProperty("fieldReference", "linktext");

    ddmForm.addDDMFormField(linkText);

    var linkUrl = new DDMFormField("linkurl", DDMFormFieldTypeConstants.TEXT);
    linkUrl.setLocalizable(true);
    linkUrl.setRequired(false);
    linkUrl.setIndexType("keyword");
    linkUrl.setDataType("string");
    linkUrl.setLabel(lv(fi, sv, en, "Linkki", "Länk", "Link"));
    linkUrl.setProperty("fieldReference", "linkurl");
    ddmForm.addDDMFormField(linkUrl);

    var layout = oneColumnLayout(fi, "linktext", "linkurl");

    var nameMap = localizeMap(fi, sv, en, "Linkki", "Länk", "Link");
    var descMap =
        localizeMap(
            fi,
            sv,
            en,
            "Erillinen linkki-field set",
            "Fristående länkuppsättning",
            "Reusable link field set");

    var sc = serviceContext(userId);

    var classNameId = PortalUtil.getClassNameId(JournalArticle.class.getName());

    return ddmStructureLocalService.addStructure(
        FIELDSET_KEY,
        userId,
        JOD_GROUP_ID,
        0,
        classNameId,
        FIELDSET_KEY,
        nameMap,
        descMap,
        ddmForm,
        layout,
        StorageType.DEFAULT.toString(),
        DDMStructureConstants.TYPE_FRAGMENT,
        sc);
  }

  private DDMStructure addMainStructure(
      long userId, Locale fi, Locale sv, Locale en, DDMStructure linkFieldSet)
      throws PortalException {

    var ddmForm = new DDMForm();
    ddmForm.setAvailableLocales(new HashSet<>(Arrays.asList(fi, sv, en)));
    ddmForm.setDefaultLocale(fi);

    var ingress = new DDMFormField("ingress", DDMFormFieldTypeConstants.TEXT);
    ingress.setLocalizable(true);
    ingress.setRequired(true);
    ingress.setIndexType("text");
    ingress.setDataType("string");
    ingress.setLabel(lv(fi, sv, en, "Tiivistelmä", "Ingress", "Ingress"));
    ingress.setProperty("fieldReference", "ingress");
    ddmForm.addDDMFormField(ingress);

    var content = new DDMFormField("content", DDMFormFieldTypeConstants.RICH_TEXT);
    content.setLocalizable(true);
    content.setRequired(true);
    content.setIndexType("text");
    content.setDataType("html");
    content.setLabel(lv(fi, sv, en, "Sisältö", "Description", "Content"));
    content.setProperty("fieldReference", "content");
    ddmForm.addDDMFormField(content);

    var image = new DDMFormField("image", DDMFormFieldTypeConstants.IMAGE);
    image.setLocalizable(true);
    image.setRequired(false);
    image.setIndexType("keyword");
    image.setDataType("image");
    image.setLabel(lv(fi, sv, en, "Kuva", "Image", "Image"));
    image.setProperty("fieldReference", "image");
    image.setProperty("dataType", "image");
    ddmForm.addDDMFormField(image);

    var linkRef = new DDMFormField(FIELDSET_FIELD_NAME, DDMFormFieldTypeConstants.FIELDSET);
    linkRef.setLocalizable(false);
    linkRef.setRequired(false);
    linkRef.setIndexType("none");
    linkRef.setRepeatable(true);
    linkRef.setLabel(lv(fi, sv, en, "Linkki", "Länk", "Link"));
    linkRef.setProperty("fieldReference", "link");

    linkRef.setProperty("ddmStructureId", linkFieldSet.getStructureId());
    linkRef.setProperty("ddmStructureKey", linkFieldSet.getStructureKey());

    var linkFsLayout =
        ddmStructureLayoutLocalService.fetchStructureLayout(
            linkFieldSet.getGroupId(),
            linkFieldSet.getClassNameId(),
            linkFieldSet.getStructureKey());
    if (linkFsLayout != null) {
      linkRef.setProperty("ddmStructureLayoutId", linkFsLayout.getStructureLayoutId());
    }

    var rows = new ArrayList<Map<String, Object>>();
    rows.add(Map.of("columns", List.of(Map.of("size", 12, "fields", List.of("LinkText")))));
    rows.add(Map.of("columns", List.of(Map.of("size", 12, "fields", List.of("LinkUrl")))));
    linkRef.setProperty("rows", rows);

    linkRef.setNestedDDMFormFields(java.util.Collections.emptyList());

    ddmForm.addDDMFormField(linkRef);

    var layout = oneColumnLayout(fi, "ingress", "content", "image", FIELDSET_FIELD_NAME);

    var nameMap =
        localizeMap(fi, sv, en, "Koulutustarjonta", "Utbildningserbjudande", "Study Program");
    var descMap =
        localizeMap(
            fi,
            sv,
            en,
            "Ohjaaja CMS:n koulutustarjontarakenteet",
            "Struktur för utbildningserbjudande i Ohjaaja CMS",
            "Study program structure for Ohjaaja CMS");

    var sc = serviceContext(userId);

    var classNameId = PortalUtil.getClassNameId(JournalArticle.class.getName());

    return ddmStructureLocalService.addStructure(
        EXTERNAL_REFERENCE_CODE,
        userId,
        JOD_GROUP_ID,
        0,
        classNameId,
        EXTERNAL_REFERENCE_CODE,
        nameMap,
        descMap,
        ddmForm,
        layout,
        StorageType.DEFAULT.toString(),
        DDMStructureConstants.TYPE_DEFAULT,
        sc);
  }

  private static Map<Locale, String> localizeMap(
      Locale fi, Locale sv, Locale en, String fiVal, String svVal, String enVal) {
    var map = new HashMap<Locale, String>();
    if (Validator.isNotNull(fiVal)) map.put(fi, fiVal);
    if (Validator.isNotNull(svVal)) map.put(sv, svVal);
    if (Validator.isNotNull(enVal)) map.put(en, enVal);
    return map;
  }

  private static LocalizedValue lv(
      Locale fi, Locale sv, Locale en, String fiVal, String svVal, String enVal) {
    var v = StudyProgramImporterUtil.localized(fiVal, fi);
    v.addString(en, enVal);
    v.addString(sv, svVal);
    return v;
  }

  private static DDMFormLayout oneColumnLayout(Locale defaultLocale, String... fieldNames) {
    var layout = new DDMFormLayout();
    layout.setDefaultLocale(defaultLocale);

    var pages = new ArrayList<DDMFormLayoutPage>();
    var page = new DDMFormLayoutPage();
    page.setTitle(lv(defaultLocale, null, null, "", "", ""));
    page.setDescription(lv(defaultLocale, null, null, "", "", ""));

    var rows = new ArrayList<DDMFormLayoutRow>();
    for (var name : fieldNames) {
      var row = new DDMFormLayoutRow();
      var col = new DDMFormLayoutColumn(12, name);
      row.setDDMFormLayoutColumns(Collections.singletonList(col));
      rows.add(row);
    }
    page.setDDMFormLayoutRows(rows);
    pages.add(page);

    layout.setDDMFormLayoutPages(pages);
    layout.setPaginationMode(DDMFormLayout.SINGLE_PAGE_MODE);

    return layout;
  }

  private static ServiceContext serviceContext(long userId) {
    var sc = new ServiceContext();
    sc.setScopeGroupId(JOD_GROUP_ID);
    sc.setAddGroupPermissions(true);
    sc.setAddGuestPermissions(true);
    sc.setUserId(userId);
    return sc;
  }
}
