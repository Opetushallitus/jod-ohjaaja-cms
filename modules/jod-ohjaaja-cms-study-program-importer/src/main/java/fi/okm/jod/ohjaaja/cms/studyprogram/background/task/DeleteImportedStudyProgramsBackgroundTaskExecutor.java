/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.studyprogram.background.task;

import static fi.okm.jod.ohjaaja.cms.studyprogram.constants.StudyProgramImporterConstants.JOD_GROUP_ID;

import com.liferay.journal.service.JournalArticleLocalService;
import com.liferay.portal.kernel.backgroundtask.*;
import com.liferay.portal.kernel.backgroundtask.constants.BackgroundTaskConstants;
import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import fi.okm.jod.ohjaaja.cms.navigation.service.NavigationService;
import fi.okm.jod.ohjaaja.cms.studyprogram.service.StudyProgramFileService;
import fi.okm.jod.ohjaaja.cms.studyprogram.service.StudyProgramStructureService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(
    property =
        "background.task.executor.class.name=fi.okm.jod.ohjaaja.cms.studyprogram.background.task.DeleteImportedStudyProgramsBackgroundTaskExecutor",
    service = BackgroundTaskExecutor.class)
public class DeleteImportedStudyProgramsBackgroundTaskExecutor
    extends BaseStudyProgramBackgroundTaskExecutor {

  private static final Log log =
      LogFactoryUtil.getLog(DeleteImportedStudyProgramsBackgroundTaskExecutor.class);

  @Reference private StudyProgramFileService studyProgramFileService;
  @Reference private StudyProgramStructureService studyProgramStructureService;
  @Reference private JournalArticleLocalService journalArticleLocalService;
  @Reference private NavigationService navigationService;

  public DeleteImportedStudyProgramsBackgroundTaskExecutor() {
    setIsolationLevel(BackgroundTaskConstants.ISOLATION_LEVEL_COMPANY);
  }

  @Override
  public BackgroundTaskResult execute(BackgroundTask backgroundTask) throws PortalException {
    BackgroundTaskStatus status =
        BackgroundTaskStatusRegistryUtil.getBackgroundTaskStatus(
            backgroundTask.getBackgroundTaskId());

    status.setAttribute("phase", "studyprogram.deleting");
    status.setAttribute("progress", 0);

    var serviceContext = getServiceContext();

    var structure = studyProgramStructureService.getOrCreateDDMStructure(false);
    if (structure == null) {
      log.warn("No study programs found to delete.");
      return new BackgroundTaskResult(
          BackgroundTaskConstants.STATUS_FAILED, "No study programs found to delete.");
    }

    var articles =
        journalArticleLocalService.getArticlesByStructureId(
            JOD_GROUP_ID, structure.getStructureId(), QueryUtil.ALL_POS, QueryUtil.ALL_POS, null);

    var total = articles.size() + 2;

    for (int i = 0; i < articles.size(); i++) {
      var article = articles.get(i);
      try {
        navigationService.deleteStudyProgramNavigationMenuItem(article.getExternalReferenceCode());
        journalArticleLocalService.deleteArticle(
            JOD_GROUP_ID, article.getArticleId(), serviceContext);
        log.info("Deleted study program: " + article.getExternalReferenceCode());
      } catch (PortalException e) {
        log.error("Failed to delete study program: " + article.getExternalReferenceCode(), e);
        reportError(
            backgroundTask,
            "Failed to delete study program: "
                + article.getExternalReferenceCode()
                + " - "
                + e.getMessage());
      }

      status.setAttribute("progress", (i + 1) * 100 / total);
    }

    try {
      studyProgramStructureService.deleteDDMStructure();
    } catch (Exception e) {
      log.error("Failed to delete DDM structure", e);
      reportError(backgroundTask, "Failed to delete DDM structure: " + e.getMessage());
    }
    status.setAttribute("progress", (articles.size() + 1) * 100 / total);
    try {
      studyProgramFileService.deleteStudyProgramImageFolder();
    } catch (Exception e) {
      log.error("Failed to delete study program image folder", e);
      reportError(backgroundTask, "Failed to delete study program image folder: " + e.getMessage());
    }
    status.setAttribute("progress", 100);
    status.setAttribute("phase", "studyprogram.completed");

    return BackgroundTaskResult.SUCCESS;
  }
}
