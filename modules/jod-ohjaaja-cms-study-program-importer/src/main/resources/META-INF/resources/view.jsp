<%@ page import="com.liferay.journal.model.JournalArticle" %>
<%@ page import="com.liferay.portal.kernel.language.LanguageUtil" %>
<%@ page import="javax.portlet.PortletURL" %>
<%@ page import="javax.portlet.ResourceURL" %>
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
  importURL.setParameter("javax.portlet.action", "importAction");

  PortletURL deleteURL = renderResponse.createActionURL();
  deleteURL.setParameter("javax.portlet.action", "deleteAllAction");

  String taskId = (String) request.getAttribute("taskId");
  String action = (String) request.getAttribute("current-action");

  ResourceURL statusURL = renderResponse.createResourceURL();
  statusURL.setResourceID("getImportStatus");
  if (taskId != null) {
    statusURL.setParameter("taskId", taskId);
  }


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

  <c:if test='<%= "import".equals(action) %>'>
    <div id="<portlet:namespace />message" class="text-muted small mb-2"></div>
    <div class="progress-group progress-info">
      <div class="progress">
        <div
            id="<portlet:namespace />progress-value"
            aria-valuenow="0"
            aria-valuemin="0"
            aria-valuemax="100"
            class="progress-bar"
            role="progressbar"
            style="width:0;"
        >

        </div>
      </div>
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

    <c:if test='<%= "delete".equals(action) %>'>
      <div id="<portlet:namespace />message" class="text-muted small mb-2"></div>
      <div class="progress-group progress-info">
        <div class="progress">
          <div
              id="<portlet:namespace />progress-value"
              aria-valuenow="0"
              aria-valuemin="0"
              aria-valuemax="100"
              class="progress-bar"
              role="progressbar"
              style="width:0;"
          >

          </div>
        </div>
      </div>
    </c:if>
  </c:if>
</c:if>


</div>

<script>
  function pollImportStatus(taskId) {
    const importButton = document.querySelector("#<portlet:namespace />importButton");
    importButton.disabled = true;
    const deleteButton = document.querySelector("#<portlet:namespace />deleteButton");
    if (deleteButton) {
      deleteButton.disabled = true;
    }

    const url = '<%= statusURL.toString() %>';

    fetch(url.toString())
        .then((res) => res.json())
        .then((data) => {

          document.querySelector("#<portlet:namespace />message").innerText = data.message;
          document.querySelector("#<portlet:namespace />progress-value").innerText = data.progress + '%';
          document.querySelector("#<portlet:namespace />progress-value").style.width = data.progress + '%';
          document.querySelector("#<portlet:namespace />progress-value").setAttribute('aria-valuenow', data.progress);

          if (!data.complete && !data.error) {
            setTimeout(() => pollImportStatus(taskId), 1000);
          } else {
            Liferay.Util.openToast({
              message: '<%=LanguageUtil.get(request, "studyprogram."+action+".success")%>',
              type: 'success',
              displayType: 'snackbar'
            });
            importButton.disabled = false;
            if (deleteButton) {
              deleteButton.disabled = false;
            }
            setTimeout(() => window.location.replace('<portlet:renderURL />'), 1000);

          }
        });
  }

  <c:if test="<%= taskId != null %>">
  pollImportStatus('<%= taskId %>');
  </c:if>
</script>
