/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.studyprogram.background.scheduler;

import com.liferay.petra.function.UnsafeRunnable;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskManager;
import com.liferay.portal.kernel.scheduler.*;
import com.liferay.portal.kernel.security.auth.PrincipalThreadLocal;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.util.PortalUtil;
import fi.okm.jod.ohjaaja.cms.studyprogram.background.task.ImportStudyProgramsBackgroundTaskExecutor;
import fi.okm.jod.ohjaaja.cms.util.AdminUtil;
import fi.okm.jod.ohjaaja.cms.util.JodOhjaajaCmsUtil;
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
  @Reference private JodOhjaajaCmsUtil jodOhjaajaCmsUtil;

  @Activate
  protected void activate() {
    triggerConfiguration = TriggerConfiguration.createTriggerConfiguration("0 0 16 * * ?");
  }

  @Override
  public UnsafeRunnable<Exception> getJobExecutorUnsafeRunnable() {
    return () -> {
      var companyId = PortalUtil.getDefaultCompanyId();
      AdminUtil.runAsAdmin(
          companyId,
          () -> {
            long userId = PrincipalThreadLocal.getUserId();

            ServiceContext serviceContext = new ServiceContext();
            serviceContext.setScopeGroupId(jodOhjaajaCmsUtil.getJodOhjaajaCmsGroup().getGroupId());
            serviceContext.setUserId(userId);

            var taskContextMap = new HashMap<String, Serializable>();
            taskContextMap.put("errors", new ArrayList<String>());

            backgroundTaskManager.addBackgroundTask(
                userId,
                companyId,
                "Study Program Import",
                ImportStudyProgramsBackgroundTaskExecutor.class.getName(),
                taskContextMap,
                serviceContext);
          });
    };
  }

  @Override
  public TriggerConfiguration getTriggerConfiguration() {
    return triggerConfiguration;
  }
}
