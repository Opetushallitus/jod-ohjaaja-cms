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
import static fi.okm.jod.ohjaaja.cms.studyprogram.util.StudyProgramImporterUtil.getUser;

import com.liferay.portal.background.task.service.BackgroundTaskLocalServiceUtil;
import com.liferay.portal.kernel.backgroundtask.BackgroundTask;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskExecutor;
import com.liferay.portal.kernel.backgroundtask.BaseBackgroundTaskExecutor;
import com.liferay.portal.kernel.backgroundtask.display.BackgroundTaskDisplay;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.util.PortalUtil;
import java.util.ArrayList;

public abstract class BaseStudyProgramBackgroundTaskExecutor extends BaseBackgroundTaskExecutor {

  private static final Log log =
      LogFactoryUtil.getLog(BaseStudyProgramBackgroundTaskExecutor.class);

  @Override
  public BackgroundTaskExecutor clone() {
    return this;
  }

  @Override
  public BackgroundTaskDisplay getBackgroundTaskDisplay(BackgroundTask backgroundTask) {
    return null;
  }

  protected ServiceContext getServiceContext() throws PortalException {
    var user = getUser(PortalUtil.getDefaultCompanyId());

    var serviceContext = new ServiceContext();
    serviceContext.setAddGroupPermissions(true);
    serviceContext.setAddGuestPermissions(true);
    serviceContext.setCompanyId(PortalUtil.getDefaultCompanyId());
    serviceContext.setScopeGroupId(JOD_GROUP_ID);
    serviceContext.setUserId(user.getUserId());
    return serviceContext;
  }

  protected void reportError(BackgroundTask task, String errorMessage) {
    try {
      var taskModel = BackgroundTaskLocalServiceUtil.getBackgroundTask(task.getBackgroundTaskId());
      var taskContextMap = taskModel.getTaskContextMap();
      @SuppressWarnings("unchecked")
      ArrayList<String> errors =
          (ArrayList<String>) taskContextMap.getOrDefault("errors", new ArrayList<String>());
      errors.add(errorMessage);
      taskContextMap.put("errors", errors);
      BackgroundTaskLocalServiceUtil.updateBackgroundTask(taskModel);
    } catch (PortalException ex) {
      log.error("Failed to update background task with error", ex);
    }
  }
}
