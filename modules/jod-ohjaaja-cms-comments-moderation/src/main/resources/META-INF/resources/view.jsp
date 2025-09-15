<%@ page import="com.liferay.portal.kernel.servlet.SessionErrors" %>
<%@ page import="com.liferay.portal.kernel.servlet.SessionMessages" %>
<%@ page import="com.liferay.portal.kernel.util.DateFormatFactoryUtil" %>
<%@ page import="com.liferay.portal.kernel.util.HtmlUtil" %>
<%@ page import="fi.okm.jod.ohjaaja.cms.comments.moderation.dto.CommentReportSummaryDto" %>
<%@ page import="javax.portlet.PortletRequest" %>
<%@ page import="java.sql.Date" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Locale" %>
<%@ page import="java.util.Optional" %>
<%@ taglib prefix="clay" uri="http://liferay.com/tld/clay" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@include file="init.jsp" %>
<%
  List<CommentReportSummaryDto> commentReportSummaries = Optional.ofNullable(request.getAttribute("commentReportSummaries")).filter(attr -> attr instanceof List<?>)
      .map(attr -> (List<?>) attr)
      .filter(list -> list.stream().allMatch(e -> e instanceof CommentReportSummaryDto))
      .map(list -> list.stream()
          .map(CommentReportSummaryDto.class::cast)
          .toList())
      .orElse(List.of());
  boolean commentsEnabled = Boolean.TRUE.equals(request.getAttribute("commentsEnabled"));
  String ohjaajaArticleShortUrlPrefix = (String) request.getAttribute("ohjaajaArticleShortUrlPrefix");
  PortletRequest portletRequest = (PortletRequest) request.getAttribute("javax.portlet.request");

  Locale userLocale = themeDisplay.getLocale();
  DateFormat dateFormat = DateFormatFactoryUtil.getDateTime(userLocale, timeZone);
%>
<div class="container bg-white mt-4 p-5">
  <h2>
    <liferay-ui:message key="page.title"/>
  </h2>
  <p>
    <% if (commentsEnabled) { %>
    <liferay-ui:message key="comments.enabled"/>
    <% } else { %>
    <liferay-ui:message key="comments.disabled"/>
    <% } %>
    <portlet:actionURL name="setCommentsFeatureFlag" var="toggleCommentsFeatureFlagURL">
      <portlet:param name="enabled" value="<%=Boolean.toString(!commentsEnabled)%>"/>
    </portlet:actionURL>
    <aui:button
        value='<%= commentsEnabled ? themeDisplay.translate("button.disable.comments") : themeDisplay.translate("button.enable.comments") %>'
        data-url="${toggleCommentsFeatureFlagURL}"
        data-msg='<%= commentsEnabled ? themeDisplay.translate("confirm.disable.comments") : themeDisplay.translate("confirm.enable.comments")%>'
        onClick="return handleConfirmationRequiringButtonClick(this);"
        cssClass="btn btn-sm"/>
  </p>


  <p>
    <liferay-ui:message key="page.description"/>
  </p>

  <% if (!SessionErrors.isEmpty(portletRequest)) { %>
  <div class="alert alert-danger">
    <ul>
      <c:forEach var="error" items="<%=SessionErrors.keySet(portletRequest)%>">
        <li><liferay-ui:message key="${error}"/></li>
      </c:forEach>
    </ul>
  </div>
  <% } %>


  <%if (!SessionMessages.isEmpty(portletRequest)) { %>
  <div class="alert alert-success">
    <ul>
      <c:forEach var="message" items="<%=SessionMessages.keySet(portletRequest)%>">
        <li><liferay-ui:message key="${message}"/></li>
      </c:forEach>
    </ul>
  </div>
  <% } %>


  <c:choose>
    <c:when test="${empty commentReportSummaries}">
      <div class="alert alert-info">
        <liferay-ui:message key="no.comment.reports"/>
      </div>
    </c:when>
    <c:otherwise>

      <table class="table table-striped">
        <thead>
        <tr>
          <th style="width: 40%"><liferay-ui:message key="comment"/></th>
          <th><liferay-ui:message key="comment.time"/></th>
          <th><liferay-ui:message key="registered.count"/></th>
          <th><liferay-ui:message key="anonymous.count"/></th>
          <th><liferay-ui:message key="latest.report"/></th>
          <th style="width: 180px"><liferay-ui:message key="actions"/></th>
        </tr>
        </thead>
        <tbody>
        <% for (CommentReportSummaryDto summary : commentReportSummaries) { %>

        <portlet:actionURL name="deleteCommentAction" var="deleteCommentURL">
          <portlet:param name="commentId" value="<%=summary.artikkelinKommenttiId().toString()%>"/>
        </portlet:actionURL>

        <portlet:actionURL name="deleteCommentReportsAction" var="deleteReportURL">
          <portlet:param name="commentId" value="<%=summary.artikkelinKommenttiId().toString()%>"/>
        </portlet:actionURL>

        <tr>
          <td style="max-width: 400px; white-space: pre-line; overflow: hidden; text-overflow: ellipsis; word-break: break-word;"
              title="<%=HtmlUtil.escape(summary.kommentti())%>">
            <div style="max-height: 280px; overflow: auto;">
              <%=HtmlUtil.escape(summary.kommentti())%>
            </div>
          </td>
          <td><%=dateFormat.format(Date.from(summary.kommentinAika()))%>
          </td>
          <td><%=summary.kirjautuneetMaara()%>
          </td>
          <td><%=summary.anonyymitMaara()%>
          </td>
          <td><%=dateFormat.format(Date.from(summary.viimeisinIlmianto()))%>
          </td>
          <td>
            <div style="display: flex; flex-direction: column; gap: 0.5em;">
              <aui:button
                  value='<%= themeDisplay.translate("button.delete.comment")%>'
                  data-url="${deleteCommentURL}"
                  data-msg='<%= themeDisplay.translate("confirm.delete.comment\")%>'
                  onClick="return handleConfirmationRequiringButtonClick(this);"
                  cssClass="btn btn-danger btn-sm"/>

              <aui:button
                  value='<%= themeDisplay.translate("button.delete.reports")%>'
                  data-url="${deleteReportURL}"
                  data-msg='<%= themeDisplay.translate("confirm.delete.reports\")%>'
                  onClick="return handleConfirmationRequiringButtonClick(this);"
                  cssClass="btn btn-warning btn-sm"/>

              <aui:button
                  value='<%= themeDisplay.translate("button.view.article")%>'
                  href="<%=ohjaajaArticleShortUrlPrefix+summary.artikkeliErc()%>"
                  target="_blank"
                  cssClass="btn btn-secondary btn-sm"
              />
            </div>
          </td>
        </tr>
        <% } %>
        </tbody>
      </table>
    </c:otherwise>
  </c:choose>
</div>

<script>
  function handleConfirmationRequiringButtonClick(button) {
    const url = button.getAttribute('data-url');
    const message = button.getAttribute('data-msg');
    if (confirm(message)) {
      window.location.href = url;
    }
    return false;
  }
</script>

