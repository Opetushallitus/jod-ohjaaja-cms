/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.studyprogram.background.task;

import static fi.okm.jod.ohjaaja.cms.studyprogram.constants.StudyProgramImporterConstants.*;
import static fi.okm.jod.ohjaaja.cms.studyprogram.constants.StudyProgramImporterConstants.ENGLISH;
import static fi.okm.jod.ohjaaja.cms.studyprogram.constants.StudyProgramImporterConstants.FINNISH;
import static fi.okm.jod.ohjaaja.cms.studyprogram.constants.StudyProgramImporterConstants.JOD_GROUP_ID;
import static fi.okm.jod.ohjaaja.cms.studyprogram.constants.StudyProgramImporterConstants.SWEDISH;
import static fi.okm.jod.ohjaaja.cms.studyprogram.util.StudyProgramImporterUtil.*;

import com.liferay.dynamic.data.mapping.model.DDMStructure;
import com.liferay.dynamic.data.mapping.model.LocalizedValue;
import com.liferay.dynamic.data.mapping.model.UnlocalizedValue;
import com.liferay.dynamic.data.mapping.storage.DDMFormFieldValue;
import com.liferay.dynamic.data.mapping.storage.DDMFormValues;
import com.liferay.dynamic.data.mapping.util.DDM;
import com.liferay.journal.model.JournalArticleModel;
import com.liferay.journal.service.JournalArticleLocalService;
import com.liferay.journal.util.JournalConverter;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.background.task.service.BackgroundTaskLocalServiceUtil;
import com.liferay.portal.kernel.backgroundtask.*;
import com.liferay.portal.kernel.backgroundtask.constants.BackgroundTaskConstants;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.util.HtmlUtil;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.Validator;
import fi.okm.jod.ohjaaja.cms.navigation.service.NavigationService;
import fi.okm.jod.ohjaaja.cms.studyprogram.client.KonfoClient;
import fi.okm.jod.ohjaaja.cms.studyprogram.dto.StudyProgramDto;
import fi.okm.jod.ohjaaja.cms.studyprogram.service.StudyProgramCategoryService;
import fi.okm.jod.ohjaaja.cms.studyprogram.service.StudyProgramFileService;
import fi.okm.jod.ohjaaja.cms.studyprogram.service.StudyProgramService;
import fi.okm.jod.ohjaaja.cms.studyprogram.service.StudyProgramStructureService;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.validation.ValidationException;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(
    property =
        "background.task.executor.class.name=fi.okm.jod.ohjaaja.cms.studyprogram.background.task.ImportStudyProgramsBackgroundTaskExecutor",
    service = BackgroundTaskExecutor.class)
public class ImportStudyProgramsBackgroundTaskExecutor
    extends BaseStudyProgramBackgroundTaskExecutor {

  private static final Log log =
      LogFactoryUtil.getLog(ImportStudyProgramsBackgroundTaskExecutor.class);

  @Reference private DDM ddm;
  @Reference private JournalConverter journalConverter;
  @Reference private KonfoClient konfoClient;
  @Reference private StudyProgramService studyProgramImporter;
  @Reference private StudyProgramFileService studyProgramFileService;
  @Reference private StudyProgramStructureService studyProgramStructureService;
  @Reference private StudyProgramCategoryService studyProgramCategoryService;
  @Reference private JournalArticleLocalService journalArticleLocalService;
  @Reference private NavigationService navigationService;

  public ImportStudyProgramsBackgroundTaskExecutor() {
    setIsolationLevel(BackgroundTaskConstants.ISOLATION_LEVEL_COMPANY);
  }

  @Override
  public BackgroundTaskResult execute(BackgroundTask backgroundTask) throws Exception {
    var status =
        BackgroundTaskStatusRegistryUtil.getBackgroundTaskStatus(
            backgroundTask.getBackgroundTaskId());

    status.setAttribute("phase", "studyprogram.importing");
    status.setAttribute("progress", 0);
    var structure = studyProgramStructureService.getOrCreateDDMStructure(true);
    if (structure == null) {
      log.error(
          "DDM structure with external reference code "
              + EXTERNAL_REFERENCE_CODE
              + " not found in group "
              + JOD_GROUP_ID);
      return new BackgroundTaskResult(
          BackgroundTaskConstants.STATUS_FAILED, "Failed to fetch or create DDM structure.");
    }

    // Ensure the navigation menu item exists
    navigationService.getStudyProgramsParentMenuItem();

    var studyPrograms = konfoClient.fetchStudyPrograms();
    var user = getUser(PortalUtil.getDefaultCompanyId());
    var serviceContext = getServiceContext();
    var currentStudyProgramArticleExternalReferenceCodes =
        studyProgramImporter.getImportedStudyPrograms().stream()
            .map(JournalArticleModel::getExternalReferenceCode)
            .toList();
    var importedStudyPrograms = new ArrayList<String>();

    for (int i = 0; i < studyPrograms.size(); i++) {

      if (Thread.interrupted()) {
        throw new InterruptedException("Background task interrupted");
      }

      // Optionally check if task is being removed externally
      var task =
          BackgroundTaskLocalServiceUtil.fetchBackgroundTask(backgroundTask.getBackgroundTaskId());
      if (task == null || task.getStatus() == BackgroundTaskConstants.STATUS_CANCELLED) {
        return BackgroundTaskResult.SUCCESS;
      }

      var studyProgram = studyPrograms.get(i);

      importStudyProgram(studyProgram, structure, user, backgroundTask, serviceContext);
      importedStudyPrograms.add(studyProgram.oid());

      status.setAttribute("progress", (i + 1) * 100 / studyPrograms.size());
    }

    currentStudyProgramArticleExternalReferenceCodes.stream()
        .filter(externalReferenceCode -> !importedStudyPrograms.contains(externalReferenceCode))
        .forEach(
            externalReferenceCode -> {
              try {
                var journalArticle =
                    journalArticleLocalService.fetchLatestArticleByExternalReferenceCode(
                        JOD_GROUP_ID, externalReferenceCode);
                if (journalArticle != null) {
                  navigationService.deleteStudyProgramNavigationMenuItem(externalReferenceCode);
                  journalArticleLocalService.deleteArticle(
                      JOD_GROUP_ID, journalArticle.getArticleId(), serviceContext);
                  log.info("Deleted study program: " + externalReferenceCode);
                }
              } catch (PortalException e) {
                log.error("Failed to delete study program: " + externalReferenceCode, e);
              }
            });

    return BackgroundTaskResult.SUCCESS;
  }

  private void importStudyProgram(
      StudyProgramDto studyProgram,
      DDMStructure structure,
      User user,
      BackgroundTask task,
      ServiceContext serviceContext) {
    try {
      if (studyProgram.kuvaus() == null || studyProgram.nimi() == null) {
        throw new ValidationException("Kuvaus or Nimi is null.");
      }

      var journalArticle =
          journalArticleLocalService.fetchLatestArticleByExternalReferenceCode(
              JOD_GROUP_ID, studyProgram.oid());

      var titleMap =
          Map.of(
              FINNISH,
              studyProgram.nimi().getOrDefault("fi", ""),
              ENGLISH,
              studyProgram.nimi().getOrDefault("en", studyProgram.nimi().getOrDefault("fi", "")),
              SWEDISH,
              studyProgram.nimi().getOrDefault("sv", studyProgram.nimi().getOrDefault("fi", "")));

      var content = getContent(structure, studyProgram);

      if (journalArticle != null) {
        log.info("Study program already imported: " + studyProgram.oid() + ". Updating article.");

        journalArticleLocalService.updateArticle(
            journalArticle.getUserId(),
            journalArticle.getGroupId(),
            journalArticle.getFolderId(),
            journalArticle.getArticleId(),
            journalArticle.getVersion(),
            titleMap,
            journalArticle.getDescriptionMap(),
            content,
            journalArticle.getLayoutUuid(),
            serviceContext);

      } else {
        log.info("Importing study program: " + studyProgram.oid());

        journalArticle =
            journalArticleLocalService.addArticle(
                studyProgram.oid(),
                user.getUserId(),
                JOD_GROUP_ID,
                0,
                titleMap,
                null,
                content,
                structure.getStructureId(),
                null,
                serviceContext);
      }
      navigationService.addOrUpdateStudyProgramNavigationMenuItem(journalArticle, serviceContext);
      studyProgramCategoryService.setJournalArticleCategories(journalArticle);
    } catch (Exception e) {
      log.error("Failed to import study program: " + studyProgram.oid(), e);
      reportError(
          task, "Failed to import study program: " + studyProgram.oid() + " - " + e.getMessage());
    }
  }

  private String extractIngress(String text) {
    return HtmlUtil.stripHtml(text).split("\\.")[0].trim() + ".";
  }

  private DDMFormFieldValue createLinkFieldsetValue(
      StudyProgramDto studyProgram, Locale defaultLocale) {

    var fieldset = new DDMFormFieldValue();
    fieldset.setName("FieldsetLink");
    fieldset.setValue(new UnlocalizedValue(StringPool.BLANK));

    var linkText = new DDMFormFieldValue();
    linkText.setName("linktext");
    var textVal = new LocalizedValue(defaultLocale);
    var fiName = studyProgram.nimi().getOrDefault("fi", "");
    var enName = studyProgram.nimi().getOrDefault("en", fiName);
    var svName = studyProgram.nimi().getOrDefault("sv", fiName);
    textVal.addString(FINNISH, fiName);
    textVal.addString(ENGLISH, enName);
    textVal.addString(SWEDISH, svName);
    linkText.setValue(textVal);

    var linkUrl = new DDMFormFieldValue();
    linkUrl.setName("linkurl");
    var urlVal = new LocalizedValue(defaultLocale);
    var baseUrl = "https://opintopolku.fi/konfo/";
    urlVal.addString(FINNISH, baseUrl + "fi/koulutus/" + studyProgram.oid());
    urlVal.addString(ENGLISH, baseUrl + "en/koulutus/" + studyProgram.oid());
    urlVal.addString(SWEDISH, baseUrl + "sv/koulutus/" + studyProgram.oid());
    linkUrl.setValue(urlVal);

    fieldset.addNestedDDMFormFieldValue(linkText);
    fieldset.addNestedDDMFormFieldValue(linkUrl);

    return fieldset;
  }

  private String getContent(DDMStructure structure, StudyProgramDto studyProgram) throws Exception {
    var ddmForm = structure.getDDMForm();
    var formValues = new DDMFormValues(ddmForm);
    formValues.setAvailableLocales(Set.of(FINNISH, ENGLISH, SWEDISH));
    formValues.setDefaultLocale(FINNISH);

    formValues.addDDMFormFieldValue(
        createFieldValue(
            "ingress",
            Map.of(
                FINNISH, extractIngress(studyProgram.getKuvaus("fi")),
                ENGLISH, extractIngress(studyProgram.getKuvaus("en")),
                SWEDISH, extractIngress(studyProgram.getKuvaus("sv")))));

    if (Validator.isNotNull(studyProgram.teemakuva())) {
      var imageUrl = studyProgram.teemakuva();
      var json = studyProgramFileService.getImageJSON(imageUrl, studyProgram.oid());
      if (json != null) {
        formValues.addDDMFormFieldValue(createUnlocalizedFieldValue("image", json));
      } else {
        log.warn("Image import failed " + studyProgram.teemakuva());
        formValues.addDDMFormFieldValue(createUnlocalizedFieldValue("image", ""));
      }
    } else {
      formValues.addDDMFormFieldValue(createUnlocalizedFieldValue("image", ""));
    }

    formValues.addDDMFormFieldValue(
        createFieldValue(
            "content",
            Map.of(
                FINNISH, studyProgram.getKuvaus("fi"),
                ENGLISH, studyProgram.getKuvaus("en"),
                SWEDISH, studyProgram.getKuvaus("sv"))));

    formValues.addDDMFormFieldValue(
        createLinkFieldsetValue(studyProgram, formValues.getDefaultLocale()));
    return journalConverter.getContent(
        structure, ddm.getFields(structure.getStructureId(), formValues), JOD_GROUP_ID);
  }
}
