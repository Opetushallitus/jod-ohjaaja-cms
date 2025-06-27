/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.studyprogram.background.scheduler;

import static fi.okm.jod.ohjaaja.cms.studyprogram.util.StudyProgramImporterUtil.getUser;

import com.liferay.petra.function.UnsafeRunnable;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskManager;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.scheduler.*;
import com.liferay.portal.kernel.security.permission.PermissionCheckerFactoryUtil;
import com.liferay.portal.kernel.security.permission.PermissionThreadLocal;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.util.PortalUtil;
import fi.okm.jod.ohjaaja.cms.studyprogram.background.task.ImportStudyProgramsBackgroundTaskExecutor;
import fi.okm.jod.ohjaaja.cms.studyprogram.constants.StudyProgramImporterConstants;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = SchedulerJobConfiguration.class)
public class ImportStudyProgramsSchedulerJobConfiguration implements SchedulerJobConfiguration {

  private TriggerConfiguration triggerConfiguration;
  @Reference private BackgroundTaskManager backgroundTaskManager;

  @Activate
  protected void activate() {
    triggerConfiguration =
        TriggerConfiguration.createTriggerConfiguration("0 0 0 * * ?"); // Daily at midnight GMT
  }

  @Override
  public UnsafeRunnable<Exception> getJobExecutorUnsafeRunnable() {
    return () -> {
      User user = getUser(PortalUtil.getDefaultCompanyId());
      long userId = user.getUserId();
      var checker = PermissionCheckerFactoryUtil.create(user);
      PermissionThreadLocal.setPermissionChecker(checker);

      ServiceContext serviceContext = new ServiceContext();
      serviceContext.setScopeGroupId(StudyProgramImporterConstants.JOD_GROUP_ID);
      serviceContext.setUserId(userId);

      var taskContextMap = new HashMap<String, Serializable>();
      taskContextMap.put("errors", new ArrayList<String>());

      backgroundTaskManager.addBackgroundTask(
          userId,
          PortalUtil.getDefaultCompanyId(),
          "Study Program Import",
          ImportStudyProgramsBackgroundTaskExecutor.class.getName(),
          taskContextMap,
          serviceContext);
    };
  }

  @Override
  public TriggerConfiguration getTriggerConfiguration() {
    return triggerConfiguration;
  }
}
