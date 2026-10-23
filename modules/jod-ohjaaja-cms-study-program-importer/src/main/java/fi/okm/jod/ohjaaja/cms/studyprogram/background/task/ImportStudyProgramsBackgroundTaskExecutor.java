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
import static fi.okm.jod.ohjaaja.cms.util.AdminUtil.getAdminUser;

import com.liferay.dynamic.data.mapping.model.DDMStructure;
import com.liferay.dynamic.data.mapping.util.DDM;
import com.liferay.journal.model.JournalArticle;
import com.liferay.journal.model.JournalArticleModel;
import com.liferay.journal.service.JournalArticleLocalService;
import com.liferay.journal.util.JournalConverter;
import com.liferay.portal.background.task.service.BackgroundTaskLocalServiceUtil;
import com.liferay.portal.kernel.backgroundtask.*;
import com.liferay.portal.kernel.backgroundtask.constants.BackgroundTaskConstants;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.PropsUtil;
import com.liferay.portal.kernel.util.Validator;
import fi.okm.jod.ohjaaja.cms.navigation.service.NavigationService;
import fi.okm.jod.ohjaaja.cms.studyprogram.client.KonfoClient;
import fi.okm.jod.ohjaaja.cms.studyprogram.dto.StudyProgramDto;
import fi.okm.jod.ohjaaja.cms.studyprogram.service.StudyProgramCategoryService;
import fi.okm.jod.ohjaaja.cms.studyprogram.service.StudyProgramFileService;
import fi.okm.jod.ohjaaja.cms.studyprogram.service.StudyProgramFingerprintService;
import fi.okm.jod.ohjaaja.cms.studyprogram.service.StudyProgramFingerprintService.ArticleState;
import fi.okm.jod.ohjaaja.cms.studyprogram.service.StudyProgramImageService;
import fi.okm.jod.ohjaaja.cms.studyprogram.service.StudyProgramImageService.ArticleImage;
import fi.okm.jod.ohjaaja.cms.studyprogram.service.StudyProgramService;
import fi.okm.jod.ohjaaja.cms.studyprogram.service.StudyProgramStructureService;
import fi.okm.jod.ohjaaja.cms.studyprogram.util.StudyProgramArticleFields;
import fi.okm.jod.ohjaaja.cms.studyprogram.util.StudyProgramArticleValues;
import fi.okm.jod.ohjaaja.cms.studyprogram.util.StudyProgramFingerprint;
import fi.okm.jod.ohjaaja.cms.util.JodOhjaajaCmsUtil;
import jakarta.validation.ValidationException;
import java.util.List;
import java.util.stream.Collectors;
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
  @Reference private StudyProgramImageService studyProgramImageService;
  @Reference private StudyProgramFingerprintService studyProgramFingerprintService;
  @Reference private StudyProgramStructureService studyProgramStructureService;
  @Reference private StudyProgramCategoryService studyProgramCategoryService;
  @Reference private JournalArticleLocalService journalArticleLocalService;
  @Reference private NavigationService navigationService;
  @Reference private JodOhjaajaCmsUtil jodOhjaajaCmsUtil;

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

    var konfoUrl = getKonfoUrl();
    if (konfoUrl == null) {
      log.error("Property " + KONFO_URL_PROPERTY + " is not set");
      return new BackgroundTaskResult(
          BackgroundTaskConstants.STATUS_FAILED, "Property " + KONFO_URL_PROPERTY + " is not set.");
    }
    var structure = studyProgramStructureService.getOrCreateDDMStructure(true);
    if (structure == null) {
      log.error(
          "DDM structure with external reference code "
              + EXTERNAL_REFERENCE_CODE
              + " not found in group "
              + getGroupId());
      return new BackgroundTaskResult(
          BackgroundTaskConstants.STATUS_FAILED, "Failed to fetch or create DDM structure.");
    }

    // Ensure the navigation menu item exists
    navigationService.getStudyProgramsParentMenuItem();
    studyProgramFingerprintService.initFingerprintField();

    var studyPrograms = konfoClient.fetchStudyPrograms();
    var context = createImportContext(backgroundTask, konfoUrl, structure);
    var currentStudyProgramArticleExternalReferenceCodes =
        studyProgramImporter.getImportedStudyPrograms().stream()
            .map(JournalArticleModel::getExternalReferenceCode)
            .toList();

    for (int i = 0; i < studyPrograms.size(); i++) {
      if (Thread.interrupted()) {
        throw new InterruptedException("Background task interrupted");
      }
      if (isRemovedOrCancelled(backgroundTask)) {
        return BackgroundTaskResult.SUCCESS;
      }

      importStudyProgram(studyPrograms.get(i), context);

      status.setAttribute("progress", (i + 1) * 100 / studyPrograms.size());
    }

    // importStudyProgram() catches and logs all exceptions, so every incoming program is kept
    // regardless of per-item outcome
    var importedStudyProgramOids =
        studyPrograms.stream().map(StudyProgramDto::oid).collect(Collectors.toSet());
    currentStudyProgramArticleExternalReferenceCodes.stream()
        .filter(externalReferenceCode -> !importedStudyProgramOids.contains(externalReferenceCode))
        .forEach(
            externalReferenceCode ->
                deleteStudyProgram(externalReferenceCode, context.serviceContext()));

    return BackgroundTaskResult.SUCCESS;
  }

  /**
   * @return the URL of the Konfo that the articles link to, without a trailing slash, since the
   *     links are built by appending paths to it, or null if it is not set
   */
  private static String getKonfoUrl() {
    var konfoUrl = PropsUtil.get(KONFO_URL_PROPERTY);
    return Validator.isBlank(konfoUrl) ? null : konfoUrl.strip().replaceFirst("/+$", "");
  }

  private ImportContext createImportContext(
      BackgroundTask backgroundTask, String konfoUrl, DDMStructure structure)
      throws PortalException {
    var user = getAdminUser(PortalUtil.getDefaultCompanyId());
    return new ImportContext(
        backgroundTask,
        konfoUrl,
        structure,
        studyProgramStructureService.getDefinitions(structure),
        // The articles that use the placeholder share it, so a changed placeholder is updated here
        // instead of only when some of those articles is updated
        studyProgramFileService.getStudyProgramImagePlaceholder(user.getUserId()),
        user,
        getServiceContext(getGroupId()));
  }

  private static boolean isRemovedOrCancelled(BackgroundTask backgroundTask) {
    var task =
        BackgroundTaskLocalServiceUtil.fetchBackgroundTask(backgroundTask.getBackgroundTaskId());
    return task == null || task.getStatus() == BackgroundTaskConstants.STATUS_CANCELLED;
  }

  private void importStudyProgram(StudyProgramDto studyProgram, ImportContext context)
      throws InterruptedException {
    try {
      if (studyProgram.kuvaus() == null || studyProgram.nimi() == null) {
        throw new ValidationException("Kuvaus or Nimi is null.");
      }

      var journalArticle =
          journalArticleLocalService.fetchLatestArticleByExternalReferenceCode(
              getGroupId(), studyProgram.oid());

      var values = StudyProgramArticleValues.of(studyProgram, context.konfoUrl());
      var fingerprint = StudyProgramFingerprint.calculate(values, context.structureDefinitions());
      var articleState =
          journalArticle == null
              ? ArticleState.OUTDATED
              : studyProgramFingerprintService.getArticleState(journalArticle, fingerprint);

      var image = getArticleImage(values, journalArticle, articleState, context);
      // A new version of an article that was built from the same values and shows the same image
      // would have the same content
      if (articleState == ArticleState.OUTDATED
          || !studyProgramImageService.showsImage(journalArticle, image.fileEntry())) {
        var content = buildContent(context.structure(), values, image.fileEntry());
        journalArticle = addOrUpdateArticle(values, journalArticle, content, context);
      } else {
        log.info("Study program unchanged: " + studyProgram.oid() + ". Skipping article update.");
      }
      // Stored right after the article, so that a failure below does not update the article again
      // on every import. Also stored when the article was not updated, since its image may have
      // been imported now. An unchanged value is not stored again.
      studyProgramFingerprintService.setFingerprint(journalArticle, fingerprint, image.imported());
      // Updated on every import, so a failure here is retried without updating the article
      navigationService.addOrUpdateStudyProgramNavigationMenuItem(
          journalArticle, context.serviceContext());
      studyProgramCategoryService.setJournalArticleCategories(
          journalArticle, context.user().getUserId());
    } catch (InterruptedException e) {
      throw e;
    } catch (Exception e) {
      log.error("Failed to import study program: " + studyProgram.oid(), e);
      reportError(
          context.task(),
          "Failed to import study program: " + studyProgram.oid() + " - " + e.getMessage());
    }
  }

  /**
   * @param journalArticle the latest version of the article, or null if there is none yet
   */
  private ArticleImage getArticleImage(
      StudyProgramArticleValues values,
      JournalArticle journalArticle,
      ArticleState articleState,
      ImportContext context)
      throws InterruptedException {
    // The image of an article that is up to date has been imported, so it is imported again only
    // if it has been deleted, which restores it from the Recycle Bin or adds a new file. A missing
    // image is retried.
    if (articleState == ArticleState.UP_TO_DATE) {
      var shownImage = studyProgramImageService.getShownImage(journalArticle);
      if (shownImage != null) {
        return new ArticleImage(shownImage, true);
      }
    }
    return studyProgramImageService.getImage(
        values, journalArticle, context.placeholderImage(), context.user().getUserId());
  }

  /**
   * Builds the article content from the given values and image only, so that their fingerprint
   * covers everything that the article is built from. {@link StudyProgramArticleFields} places them
   * in the fields of the structure.
   */
  private String buildContent(
      DDMStructure structure, StudyProgramArticleValues values, FileEntry image) throws Exception {
    var formValues =
        StudyProgramArticleFields.createFormValues(structure.getDDMForm(), values, image);
    return journalConverter.getContent(
        structure, ddm.getFields(structure.getStructureId(), formValues), getGroupId());
  }

  private JournalArticle addOrUpdateArticle(
      StudyProgramArticleValues values,
      JournalArticle journalArticle,
      String content,
      ImportContext context)
      throws Exception {
    if (journalArticle != null) {
      log.info("Study program or its article changed: " + values.oid() + ". Updating article.");

      return journalArticleLocalService.updateArticle(
          journalArticle.getUserId(),
          journalArticle.getGroupId(),
          journalArticle.getFolderId(),
          journalArticle.getArticleId(),
          journalArticle.getVersion(),
          values.title(),
          journalArticle.getDescriptionMap(),
          content,
          journalArticle.getLayoutUuid(),
          context.serviceContext());
    }

    log.info("Importing study program: " + values.oid());

    return journalArticleLocalService.addArticle(
        values.oid(),
        context.user().getUserId(),
        getGroupId(),
        0,
        values.title(),
        null,
        content,
        context.structure().getStructureId(),
        null,
        context.serviceContext());
  }

  /** Deletes the article and menu item of a study program that is no longer in Konfo. */
  private void deleteStudyProgram(String externalReferenceCode, ServiceContext serviceContext) {
    try {
      var journalArticle =
          journalArticleLocalService.fetchLatestArticleByExternalReferenceCode(
              getGroupId(), externalReferenceCode);
      if (journalArticle != null) {
        navigationService.deleteStudyProgramNavigationMenuItem(externalReferenceCode);
        journalArticleLocalService.deleteArticle(
            getGroupId(), journalArticle.getArticleId(), serviceContext);
        log.info("Deleted study program: " + externalReferenceCode);
      }
    } catch (PortalException e) {
      log.error("Failed to delete study program: " + externalReferenceCode, e);
    }
  }

  private long getGroupId() {
    return jodOhjaajaCmsUtil.getJodOhjaajaCmsGroup().getGroupId();
  }

  /**
   * What the import of every study program uses.
   *
   * @param konfoUrl the URL of the Konfo that the articles link to
   * @param structureDefinitions the definitions of the DDM structure of the articles and the field
   *     sets that it uses
   * @param placeholderImage the image of the articles of study programs without one, or null if it
   *     is missing
   */
  private record ImportContext(
      BackgroundTask task,
      String konfoUrl,
      DDMStructure structure,
      List<String> structureDefinitions,
      FileEntry placeholderImage,
      User user,
      ServiceContext serviceContext) {}
}
