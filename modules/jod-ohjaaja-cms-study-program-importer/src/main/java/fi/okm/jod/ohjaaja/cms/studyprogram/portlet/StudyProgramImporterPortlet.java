/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.studyprogram.portlet;

import com.liferay.portal.background.task.util.comparator.BackgroundTaskCreateDateComparator;
import com.liferay.portal.kernel.backgroundtask.*;
import com.liferay.portal.kernel.backgroundtask.constants.BackgroundTaskConstants;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCPortlet;
import com.liferay.portal.kernel.servlet.SessionErrors;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.*;
import fi.okm.jod.ohjaaja.cms.studyprogram.background.task.DeleteImportedStudyProgramsBackgroundTaskExecutor;
import fi.okm.jod.ohjaaja.cms.studyprogram.background.task.ImportStudyProgramsBackgroundTaskExecutor;
import fi.okm.jod.ohjaaja.cms.studyprogram.constants.StudyProgramImporterPortletKeys;
import fi.okm.jod.ohjaaja.cms.studyprogram.service.StudyProgramBackgroundTaskService;
import fi.okm.jod.ohjaaja.cms.studyprogram.service.StudyProgramService;
import java.io.IOException;
import java.util.ArrayList;
import javax.portlet.*;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(
    immediate = true,
    property = {
      "com.liferay.portlet.css-class-wrapper=portlet-study-program-importer",
      "com.liferay.portlet.display-category=category.study-program",
      "com.liferay.portlet.preferences-owned-by-group=true",
      "com.liferay.portlet.private-request-attributes=false",
      "com.liferay.portlet.private-session-attributes=false",
      "com.liferay.portlet.remoteable=false",
      "com.liferay.portlet.render-weight=50",
      "com.liferay.portlet.use-default-template=true",
      "javax.portlet.display-name=Koulutustarjonta",
      "javax.portlet.expiration-cache=0",
      "javax.portlet.init-param.always-display-default-configuration-icons=true",
      "javax.portlet.init-param.view-template=/view.jsp",
      "javax.portlet.name=" + StudyProgramImporterPortletKeys.STUDY_PROGRAM_IMPORTER,
      "javax.portlet.resource-bundle=content.Language",
      "javax.portlet.security-role-ref=administrator,power-user",
      "javax.portlet.supports.mime-type=text/html",
      "javax.portlet.supported-locale=en_US,fi_FI"
    },
    service = Portlet.class)
public class StudyProgramImporterPortlet extends MVCPortlet {

  private static final Log log = LogFactoryUtil.getLog(StudyProgramImporterPortlet.class);
  @Reference private StudyProgramService studyProgramService;
  @Reference private StudyProgramBackgroundTaskService studyProgramBackgroundTaskService;

  @Override
  public void doView(RenderRequest renderRequest, RenderResponse renderResponse)
      throws IOException, PortletException {
    renderRequest.setAttribute(
        "importedStudyPrograms", studyProgramService.getImportedStudyPrograms());
    ThemeDisplay themeDisplay = (ThemeDisplay) renderRequest.getAttribute(WebKeys.THEME_DISPLAY);

    var importTasks =
        BackgroundTaskManagerUtil.getBackgroundTasks(
            themeDisplay.getScopeGroupId(),
            ImportStudyProgramsBackgroundTaskExecutor.class.getName(),
            0,
            1,
            BackgroundTaskCreateDateComparator.getInstance(false));

    var latestImportTask = importTasks.isEmpty() ? null : importTasks.getFirst();

    if (latestImportTask != null) {

      if (!latestImportTask.isCompleted()) {
        renderRequest.setAttribute(
            "taskId", String.valueOf(latestImportTask.getBackgroundTaskId()));
      }
      var errors =
          latestImportTask.getTaskContextMap().getOrDefault("errors", new ArrayList<String>());
      renderRequest.setAttribute("import-errors", errors);
      renderRequest.setAttribute("import-task-date", latestImportTask.getCreateDate());
    }

    var deleteTasks =
        BackgroundTaskManagerUtil.getBackgroundTasks(
            themeDisplay.getScopeGroupId(),
            DeleteImportedStudyProgramsBackgroundTaskExecutor.class.getName(),
            0,
            1,
            BackgroundTaskCreateDateComparator.getInstance(false));

    var latestDeleteTask = deleteTasks.isEmpty() ? null : deleteTasks.getFirst();

    if (latestDeleteTask != null) {

      if (!latestDeleteTask.isCompleted()) {
        renderRequest.setAttribute(
            "taskId", String.valueOf(latestDeleteTask.getBackgroundTaskId()));
      }
      var errors =
          latestDeleteTask.getTaskContextMap().getOrDefault("errors", new ArrayList<String>());
      renderRequest.setAttribute("delete-errors", errors);
      renderRequest.setAttribute("delete-task-date", latestDeleteTask.getCreateDate());
    }

    super.doView(renderRequest, renderResponse);
  }

  public void importAction(ActionRequest request, ActionResponse response) {
    ThemeDisplay themeDisplay = (ThemeDisplay) request.getAttribute(WebKeys.THEME_DISPLAY);
    try {
      var task = studyProgramBackgroundTaskService.startImportTask(themeDisplay.getUserId());
      request.setAttribute("current-action", "import");
      request.getPortletSession().setAttribute("taskId", task.getBackgroundTaskId());

    } catch (Exception e) {
      log.error("import-error", e);
      SessionErrors.add(request, "import-error", e.getMessage());
    }
  }

  @Override
  public void serveResource(ResourceRequest request, ResourceResponse response) throws IOException {
    ThemeDisplay themeDisplay = (ThemeDisplay) request.getAttribute(WebKeys.THEME_DISPLAY);

    long backgroundTaskId = ParamUtil.getLong(request, "taskId");

    JSONObject json = JSONFactoryUtil.createJSONObject();

    try {
      BackgroundTask task = BackgroundTaskManagerUtil.fetchBackgroundTask(backgroundTaskId);

      if (task != null) {
        if (task.isCompleted()) {

          json.put("progress", 100);
          json.put("message", themeDisplay.translate("studyprogram.completed"));
          json.put("status", BackgroundTaskConstants.STATUS_SUCCESSFUL);
          json.put("complete", task.isCompleted());
        } else {
          BackgroundTaskStatus status =
              BackgroundTaskStatusRegistryUtil.getBackgroundTaskStatus(task.getBackgroundTaskId());

          int progress = GetterUtil.getInteger(status.getAttribute("progress"), 0);
          String message =
              themeDisplay.translate(GetterUtil.getString(status.getAttribute("phase"), ""));

          json.put("progress", progress);
          json.put("message", message);
          json.put("status", task.getStatus());
          json.put("complete", task.isCompleted());
        }
      } else {
        json.put("error", "No such task");
      }
    } catch (Exception e) {
      json.put("error", e.getMessage());
    }

    response.setContentType(ContentTypes.APPLICATION_JSON);
    response.getWriter().write(json.toString());
  }

  public void deleteAllAction(ActionRequest request, ActionResponse response) {
    ThemeDisplay themeDisplay = (ThemeDisplay) request.getAttribute(WebKeys.THEME_DISPLAY);

    try {
      var task = studyProgramBackgroundTaskService.startDeleteTask(themeDisplay.getUserId());
      request.getPortletSession().setAttribute("taskId", task.getBackgroundTaskId());
      request.setAttribute("current-action", "delete");
    } catch (Exception e) {
      log.error("delete-error", e);
      SessionErrors.add(request, "delete-error", e.getMessage());
    }
  }
}
