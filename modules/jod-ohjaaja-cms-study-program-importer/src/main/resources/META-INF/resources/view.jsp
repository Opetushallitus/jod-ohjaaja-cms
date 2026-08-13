<%@ page import="com.liferay.journal.model.JournalArticle" %>
<%@ page import="com.liferay.portal.kernel.language.LanguageUtil" %>
<%@ page import="com.liferay.portal.kernel.language.UnicodeLanguageUtil" %>
<%@ page import="jakarta.portlet.ActionRequest" %>
<%@ page import="jakarta.portlet.PortletURL" %>
<%@ page import="jakarta.portlet.ResourceURL" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Optional" %>
<%@ page import="java.util.Date" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ taglib prefix="clay" uri="http://liferay.com/tld/clay" %>
<%@include file="init.jsp" %>

<%
  SimpleDateFormat dateFormat = new SimpleDateFormat("d.M.yyyy HH:mm");
  dateFormat.setTimeZone(themeDisplay.getTimeZone());

  List<JournalArticle> importedStudyProgramArticles = Optional.ofNullable(request.getAttribute("importedStudyPrograms"))
      .filter(attr -> attr instanceof List<?>)
      .map(attr -> (List<?>) attr)
      .filter(list -> list.stream().allMatch(e -> e instanceof JournalArticle))
      .map(list -> list.stream()
          .map(JournalArticle.class::cast)
          .toList())
      .orElse(List.of());

  List<String> importErrors = Optional.ofNullable(request.getAttribute("import-errors"))
      .filter(attr -> attr instanceof List<?>)
      .map(attr -> (List<?>) attr)
      .filter(list -> list.stream().allMatch(e -> e instanceof String))
      .map(list -> list.stream()
          .map(String.class::cast)
          .toList())
      .orElse(List.of());

  Date importDate = (Date) request.getAttribute("import-task-date");

  List<String> deleteErrors = Optional.ofNullable(request.getAttribute("delete-errors"))
      .filter(attr -> attr instanceof List<?>)
      .map(attr -> (List<?>) attr)
      .filter(list -> list.stream().allMatch(e -> e instanceof String))
      .map(list -> list.stream()
          .map(String.class::cast)
          .toList())
      .orElse(List.of());

  Date deleteDate = (Date) request.getAttribute("delete-task-date");



  PortletURL importURL = renderResponse.createActionURL();
  importURL.setParameter(ActionRequest.ACTION_NAME, "importAction");

  PortletURL deleteURL = renderResponse.createActionURL();
  deleteURL.setParameter(ActionRequest.ACTION_NAME, "deleteAllAction");

  String taskId = (String) request.getAttribute("taskId");
  String action = (String) request.getAttribute("current-action");

  ResourceURL statusURL = renderResponse.createResourceURL();
  statusURL.setResourceID("getImportStatus");
  if (taskId != null) {
    statusURL.setParameter("taskId", taskId);
  }

  // Reloaded once the task has finished, without the parameters that trigger polling
  PortletURL viewURL = renderResponse.createRenderURL();
  viewURL.setParameter("taskId", "");
  viewURL.setParameter("current-action", "");

  String successMessageKey = "delete".equals(action)
      ? "studyprogram.delete.success"
      : "studyprogram.import.success";
%>

<div class="container bg-white mt-4 p-5">

  <liferay-ui:error key="import-error" message="studyprogram.import.error" />
  <liferay-ui:error key="delete-error" message="studyprogram.delete.error" />

  <h2>
    <liferay-ui:message key="studyprogram.header"/>
  </h2>


  <h3 class="mt-4">
    <liferay-ui:message key="studyprogram.article.count.header"/>
  </h3>
  <div class="border border-secondary rounded-lg p-4 my-3">
    <div><liferay-ui:message key="studyprogram.article.count.total"/></div>
    <div class="text-7">
      <%=importedStudyProgramArticles.size() %>
    </div>
  </div>

  <h3 class="my-4">
    <liferay-ui:message key="studyprogram.import.header"/>
  </h3>

  <c:if test='<%=!importErrors.isEmpty()%>'>
    <div class="alert alert-danger">
      <h4 class="alert-heading"><liferay-ui:message key="studyprogram.import.errors.header" arguments="<%=dateFormat.format(importDate)%>"/></h4>
      <ul>
        <c:forEach items="<%= importErrors %>" var="error">
          <li><c:out value="${error}" /></li>
        </c:forEach>
      </ul>
    </div>

  </c:if>

  <form action="<%= importURL %>" method="post" name="importForm">
    <button id="<portlet:namespace />importButton" class="btn btn-primary" type="submit"
            name="import"><%=LanguageUtil.get(request, "studyprogram.import.button.label")%>
    </button>
  </form>

  <c:if test='<%= taskId != null && "import".equals(action) %>'>
    <div id="<portlet:namespace />message" class="text-muted small mb-2"></div>
    <div class="progress-group progress-info d-flex align-items-center">
      <progress
          id="<portlet:namespace />progress-value"
          class="progress-bar flex-grow-1"
          max="100"
          value="0"
      ></progress>
      <span id="<portlet:namespace />progress-text" class="ml-2" aria-hidden="true">0%</span>
    </div>
  </c:if>

<c:if test='<%=!deleteErrors.isEmpty() || !importedStudyProgramArticles.isEmpty() %>'>
  <h3 class="my-4">
    <liferay-ui:message key="studyprogram.delete.header"/>
  </h3>

  <c:if test='<%=!deleteErrors.isEmpty()%>'>
    <div class="alert alert-danger">
      <h4 class="alert-heading"><liferay-ui:message key="studyprogram.delete.errors.header" arguments="<%=dateFormat.format(deleteDate)%>"/></h4>
      <ul>
        <c:forEach items="<%= deleteErrors %>" var="error">
          <li><c:out value="${error}" /></li>
        </c:forEach>
      </ul>
    </div>

  </c:if>

  <c:if
      test='<%= !importedStudyProgramArticles.isEmpty() %>'>


    <form action="<%= deleteURL %>" method="post" name="deleteForm">
      <button id="<portlet:namespace />deleteButton" class="btn btn-danger" name="delete"
              type="submit"><%=LanguageUtil.get(request, "studyprogram.delete.button.label")%>
      </button>
    </form>

    <c:if test='<%= taskId != null && "delete".equals(action) %>'>
      <div id="<portlet:namespace />message" class="text-muted small mb-2"></div>
      <div class="progress-group progress-info d-flex align-items-center">
        <progress
            id="<portlet:namespace />progress-value"
            class="progress-bar flex-grow-1"
            max="100"
            value="0"
        ></progress>
        <span id="<portlet:namespace />progress-text" class="ml-2" aria-hidden="true">0%</span>
      </div>
    </c:if>
  </c:if>
</c:if>


</div>

<c:if test="<%= taskId != null %>">
  <aui:script>
    (function () {
      const statusUrl = '<%= statusURL.toString() %>';
      const viewUrl = '<%= viewURL.toString() %>';
      const successMessage = '<%= UnicodeLanguageUtil.get(request, successMessageKey) %>';

      const importButton = document.getElementById('<portlet:namespace />importButton');
      const deleteButton = document.getElementById('<portlet:namespace />deleteButton');
      const messageEl = document.getElementById('<portlet:namespace />message');
      const progressEl = document.getElementById('<portlet:namespace />progress-value');
      const progressTextEl = document.getElementById('<portlet:namespace />progress-text');

      function disableButtons(disabled) {
        [importButton, deleteButton].forEach((button) => {
          if (button) {
            button.disabled = disabled;
          }
        });
      }

      function showProgress(data) {
        if (messageEl) {
          messageEl.innerText = data.message || '';
        }
        if (progressEl) {
          progressEl.value = data.progress;
        }
        if (progressTextEl) {
          progressTextEl.innerText = data.progress + '%';
        }
      }

      function finish(type, message) {
        Liferay.Util.openToast({
          message: message,
          type: type,
          displayType: 'snackbar'
        });
        disableButtons(false);
        setTimeout(() => window.location.replace(viewUrl), 1000);
      }

      function pollStatus() {
        fetch(statusUrl)
            .then((res) => res.json())
            .then((data) => {
              showProgress(data);

              if (data.error) {
                finish('danger', data.error);
              } else if (data.complete) {
                finish('success', successMessage);
              } else {
                setTimeout(pollStatus, 1000);
              }
            })
            .catch((error) => finish('danger', String(error)));
      }

      disableButtons(true);
      pollStatus();
    })();
  </aui:script>
</c:if>
