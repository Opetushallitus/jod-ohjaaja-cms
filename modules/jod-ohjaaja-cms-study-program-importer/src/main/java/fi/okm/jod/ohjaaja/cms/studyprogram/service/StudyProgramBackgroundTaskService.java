/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.studyprogram.service;

import com.liferay.portal.kernel.audit.AuditMessage;
import com.liferay.portal.kernel.audit.AuditRouter;
import com.liferay.portal.kernel.backgroundtask.BackgroundTask;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskManagerUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.UserLocalService;
import fi.okm.jod.ohjaaja.cms.studyprogram.background.task.BaseStudyProgramBackgroundTaskExecutor;
import fi.okm.jod.ohjaaja.cms.studyprogram.background.task.DeleteImportedStudyProgramsBackgroundTaskExecutor;
import fi.okm.jod.ohjaaja.cms.studyprogram.background.task.ImportStudyProgramsBackgroundTaskExecutor;
import fi.okm.jod.ohjaaja.cms.studyprogram.constants.StudyProgramImporterConstants;
import fi.okm.jod.ohjaaja.cms.studyprogram.util.StudyProgramImporterUtil;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = StudyProgramBackgroundTaskService.class, immediate = true)
public class StudyProgramBackgroundTaskService {

  private static final Log log = LogFactoryUtil.getLog(StudyProgramBackgroundTaskService.class);
  private static final String IMPORT_TASK_NAME = "study-program-import";
  private static final String DELETE_TASK_NAME = "study-program-delete";

  @Reference private AuditRouter auditRouter;

  @Reference private UserLocalService userLocalService;

  public boolean isAnyImportOrDeleteTaskRunning() {
    var tasks =
        BackgroundTaskManagerUtil.getBackgroundTasks(
            StudyProgramImporterConstants.JOD_GROUP_ID,
            new String[] {
              ImportStudyProgramsBackgroundTaskExecutor.class.getName(),
              DeleteImportedStudyProgramsBackgroundTaskExecutor.class.getName()
            });

    return tasks.stream()
        .filter(StudyProgramImporterUtil::isActiveTask)
        .anyMatch(
            task -> {
              String name = task.getName();
              return IMPORT_TASK_NAME.equals(name) || DELETE_TASK_NAME.equals(name);
            });
  }

  public BackgroundTask startImportTask(long userId) throws PortalException {
    return startTask(userId, IMPORT_TASK_NAME, ImportStudyProgramsBackgroundTaskExecutor.class);
  }

  public BackgroundTask startDeleteTask(long userId) throws PortalException {
    return startTask(
        userId, DELETE_TASK_NAME, DeleteImportedStudyProgramsBackgroundTaskExecutor.class);
  }

  private BackgroundTask startTask(
      long userId,
      String taskName,
      Class<? extends BaseStudyProgramBackgroundTaskExecutor> executorClass)
      throws PortalException {
    if (isAnyImportOrDeleteTaskRunning()) {
      throw new IllegalStateException("Another import or delete task is already running");
    }
    audit(taskName, userId);
    ServiceContext serviceContext = new ServiceContext();
    serviceContext.setScopeGroupId(StudyProgramImporterConstants.JOD_GROUP_ID);
    serviceContext.setUserId(userId);

    var taskContextMap = new HashMap<String, Serializable>();
    taskContextMap.put("errors", new ArrayList<String>());

    return BackgroundTaskManagerUtil.addBackgroundTask(
        userId,
        StudyProgramImporterConstants.JOD_GROUP_ID,
        taskName,
        executorClass.getName(),
        taskContextMap,
        serviceContext);
  }

  private void audit(String action, long userId) {
    try {
      User user = userLocalService.getUser(userId);
      AuditMessage auditMessage =
          new AuditMessage(action, user.getCompanyId(), user.getUserId(), user.getFullName());
      auditRouter.route(auditMessage);
    } catch (PortalException e) {
      log.error("Audit failed for action " + action + " by user " + userId, e);
    }
  }
}
