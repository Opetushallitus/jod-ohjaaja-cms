/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.studyprogram.portlet;

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
import fi.okm.jod.ohjaaja.cms.studyprogram.constants.StudyProgramImporterPortletKeys;
import fi.okm.jod.ohjaaja.cms.studyprogram.service.StudyProgramBackgroundTaskService;
import fi.okm.jod.ohjaaja.cms.studyprogram.service.StudyProgramService;
import jakarta.portlet.*;
import java.io.IOException;
import java.util.ArrayList;
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
      "jakarta.portlet.display-name=Koulutustarjonta",
      "jakarta.portlet.expiration-cache=0",
      "jakarta.portlet.init-param.always-display-default-configuration-icons=true",
      "jakarta.portlet.init-param.view-template=/view.jsp",
      "jakarta.portlet.name=" + StudyProgramImporterPortletKeys.STUDY_PROGRAM_IMPORTER,
      "jakarta.portlet.resource-bundle=content.Language",
      "jakarta.portlet.security-role-ref=administrator,power-user",
      "jakarta.portlet.supports.mime-type=text/html",
      "jakarta.portlet.supported-locale=en_US,fi_FI"
    },
    service = Portlet.class)
public class StudyProgramImporterPortlet extends MVCPortlet {

  private static final Log log = LogFactoryUtil.getLog(StudyProgramImporterPortlet.class);

  private static final String ATTRIBUTE_TASK_ID = "taskId";
  private static final String ATTRIBUTE_CURRENT_ACTION = "current-action";
  private static final String ACTION_IMPORT = "import";
  private static final String ACTION_DELETE = "delete";
  private static final String JSON_PROGRESS = "progress";

  @Reference private StudyProgramService studyProgramService;
  @Reference private StudyProgramBackgroundTaskService studyProgramBackgroundTaskService;

  @Override
  public void doView(RenderRequest renderRequest, RenderResponse renderResponse)
      throws IOException, PortletException {
    renderRequest.setAttribute(
        "importedStudyPrograms", studyProgramService.getImportedStudyPrograms());

    var latestImportTask = studyProgramBackgroundTaskService.fetchLatestImportTask();

    if (latestImportTask != null) {
      var errors =
          latestImportTask.getTaskContextMap().getOrDefault("errors", new ArrayList<String>());
      renderRequest.setAttribute("import-errors", errors);
      renderRequest.setAttribute("import-task-date", latestImportTask.getCreateDate());
    }

    var latestDeleteTask = studyProgramBackgroundTaskService.fetchLatestDeleteTask();

    if (latestDeleteTask != null) {
      var errors =
          latestDeleteTask.getTaskContextMap().getOrDefault("errors", new ArrayList<String>());
      renderRequest.setAttribute("delete-errors", errors);
      renderRequest.setAttribute("delete-task-date", latestDeleteTask.getCreateDate());
    }

    setMonitoredTaskAttributes(renderRequest, latestImportTask, latestDeleteTask);

    super.doView(renderRequest, renderResponse);
  }

  /**
   * Resolves the task the view should poll for progress. The task started in the action phase is
   * passed on as a render parameter, because request attributes set in the action phase are not
   * available in the render phase. A task that is still running is also picked up without the
   * render parameter, so that reloading the page keeps showing the progress.
   */
  private void setMonitoredTaskAttributes(
      RenderRequest renderRequest,
      BackgroundTask latestImportTask,
      BackgroundTask latestDeleteTask) {

    String action = ParamUtil.getString(renderRequest, ATTRIBUTE_CURRENT_ACTION);
    long taskId = ParamUtil.getLong(renderRequest, ATTRIBUTE_TASK_ID);

    if (taskId <= 0 || (!ACTION_IMPORT.equals(action) && !ACTION_DELETE.equals(action))) {
      if (latestImportTask != null && !latestImportTask.isCompleted()) {
        action = ACTION_IMPORT;
        taskId = latestImportTask.getBackgroundTaskId();
      } else if (latestDeleteTask != null && !latestDeleteTask.isCompleted()) {
        action = ACTION_DELETE;
        taskId = latestDeleteTask.getBackgroundTaskId();
      } else {
        return;
      }
    }

    renderRequest.setAttribute(ATTRIBUTE_TASK_ID, String.valueOf(taskId));
    renderRequest.setAttribute(ATTRIBUTE_CURRENT_ACTION, action);
  }

  public void importAction(ActionRequest request, ActionResponse response) {
    ThemeDisplay themeDisplay = (ThemeDisplay) request.getAttribute(WebKeys.THEME_DISPLAY);
    try {
      var task = studyProgramBackgroundTaskService.startImportTask(themeDisplay.getUserId());
      setMonitoredTaskRenderParameters(response, ACTION_IMPORT, task.getBackgroundTaskId());
    } catch (Exception e) {
      log.error("import-error", e);
      SessionErrors.add(request, "import-error", e.getMessage());
    }
  }

  private void setMonitoredTaskRenderParameters(
      ActionResponse response, String action, long backgroundTaskId) {
    var renderParameters = response.getRenderParameters();
    renderParameters.setValue(ATTRIBUTE_CURRENT_ACTION, action);
    renderParameters.setValue(ATTRIBUTE_TASK_ID, String.valueOf(backgroundTaskId));
  }

  @Override
  public void serveResource(ResourceRequest request, ResourceResponse response) throws IOException {
    ThemeDisplay themeDisplay = (ThemeDisplay) request.getAttribute(WebKeys.THEME_DISPLAY);

    long backgroundTaskId = ParamUtil.getLong(request, ATTRIBUTE_TASK_ID);

    JSONObject json = JSONFactoryUtil.createJSONObject();

    try {
      BackgroundTask task = BackgroundTaskManagerUtil.fetchBackgroundTask(backgroundTaskId);

      if (task != null) {
        if (task.isCompleted()) {

          json.put(JSON_PROGRESS, 100);
          json.put("message", themeDisplay.translate("studyprogram.completed"));
          json.put("status", BackgroundTaskConstants.STATUS_SUCCESSFUL);
          json.put("complete", task.isCompleted());
        } else {
          BackgroundTaskStatus status =
              BackgroundTaskStatusRegistryUtil.getBackgroundTaskStatus(task.getBackgroundTaskId());

          // The status is registered only once the task is picked up for execution
          int progress =
              status == null ? 0 : GetterUtil.getInteger(status.getAttribute(JSON_PROGRESS), 0);
          String message =
              status == null
                  ? ""
                  : themeDisplay.translate(GetterUtil.getString(status.getAttribute("phase"), ""));

          json.put(JSON_PROGRESS, progress);
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
      setMonitoredTaskRenderParameters(response, ACTION_DELETE, task.getBackgroundTaskId());
    } catch (Exception e) {
      log.error("delete-error", e);
      SessionErrors.add(request, "delete-error", e.getMessage());
    }
  }
}
