<%@ page import="com.liferay.frontend.taglib.clay.servlet.taglib.util.NavigationItem" %>
<%@ page import="com.liferay.portal.kernel.language.LanguageUtil" %>
<%@ page import="com.liferay.portal.kernel.servlet.SessionErrors" %>
<%@ page import="com.liferay.portal.kernel.servlet.SessionMessages" %>
<%@ page import="com.liferay.portal.kernel.util.DateFormatFactoryUtil" %>
<%@ page import="com.liferay.portal.kernel.util.HtmlUtil" %>
<%@ page import="fi.okm.jod.ohjaaja.cms.comments.moderation.dto.CommentDto" %>
<%@ page import="fi.okm.jod.ohjaaja.cms.comments.moderation.dto.CommentReportSummaryDto" %>
<%@ page import="fi.okm.jod.ohjaaja.cms.comments.moderation.dto.PageDto" %>
<%@ page import="javax.portlet.PortletRequest" %>
<%@ page import="java.sql.Date" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Locale" %>
<%@ page import="java.util.Optional" %>
<%@ page import="com.liferay.portal.kernel.util.ParamUtil" %>
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


  PageDto<CommentDto> commentsPage = (PageDto<CommentDto>) request.getAttribute("commentsPage");

  boolean commentsEnabled = Boolean.TRUE.equals(request.getAttribute("commentsEnabled"));
  String ohjaajaArticleShortUrlPrefix = (String) request.getAttribute("ohjaajaArticleShortUrlPrefix");
  PortletRequest portletRequest = (PortletRequest) request.getAttribute("javax.portlet.request");

  Locale userLocale = themeDisplay.getLocale();
  DateFormat dateFormat = DateFormatFactoryUtil.getDateTime(userLocale, timeZone);

  String currentTab = ParamUtil.get(request, "tab", "reported");
  int currentPage = ParamUtil.get(request, "currentPage", 1);

  // Setup navigation items for tabs
  List<NavigationItem> navigationItems = new ArrayList<>();
  NavigationItem reportedTab = new NavigationItem();
  reportedTab.setHref(renderResponse.createRenderURL().toString() + "&" + renderResponse.getNamespace() + "tab=reported");
  reportedTab.setLabel(LanguageUtil.get(request, "reported.comments"));
  reportedTab.setActive("reported".equals(currentTab));

  NavigationItem allCommentsTab = new NavigationItem();
  allCommentsTab.setHref(renderResponse.createRenderURL().toString() + "&" + renderResponse.getNamespace() + "tab=all");
  allCommentsTab.setLabel(LanguageUtil.get(request, "all.comments"));
  allCommentsTab.setActive("all".equals(currentTab));

  navigationItems.add(reportedTab);
  navigationItems.add(allCommentsTab);
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

  <clay:navigation-bar
      navigationItems="<%= navigationItems %>"
  />
  <div class="tab-content mt-4">
    <% if ("reported".equals(currentTab)) { %>
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
            <portlet:param name="commentId"
                           value="<%=summary.artikkelinKommenttiId().toString()%>"/>
          </portlet:actionURL>

          <portlet:actionURL name="deleteCommentReportsAction" var="deleteReportURL">
            <portlet:param name="commentId"
                           value="<%=summary.artikkelinKommenttiId().toString()%>"/>
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
    <% } else { %>
    <c:choose>
      <c:when test="${commentsPage.maara() == 0}">
        <div class="alert alert-info">
          <liferay-ui:message key="no.comments"/>
        </div>
      </c:when>
      <c:otherwise>
        <table class="table table-striped">
          <thead>
          <tr>
            <th><liferay-ui:message key="comment"/></th>
            <th style="width: 180px"><liferay-ui:message key="comment.time"/></th>
            <th style="width: 180px"><liferay-ui:message key="actions"/></th>
          </tr>
          </thead>
          <tbody>
          <% for (CommentDto comment : commentsPage.sisalto()) { %>
          <portlet:actionURL name="deleteCommentAction" var="deleteCommentURL">
            <portlet:param name="commentId"
                           value="<%=comment.id().toString()%>"/>
          </portlet:actionURL>
          <tr>
            <td style="max-width: 400px; white-space: pre-line;">
              <div style="max-height: 280px; overflow: auto;">
                <%=HtmlUtil.escape(comment.kommentti())%>
              </div>
            </td>
            <td><%=dateFormat.format(Date.from(comment.luotu()))%>
            </td>
            <td>
              <div style="display: flex; flex-direction: column; gap: 0.5em;">
                <aui:button
                    value='<%= themeDisplay.translate("button.delete.comment")%>'
                    data-url="${deleteCommentURL}"
                    data-msg='<%= themeDisplay.translate("confirm.delete.comment")%>'
                    onClick="return handleConfirmationRequiringButtonClick(this);"
                    cssClass="btn btn-danger btn-sm"/>
                <aui:button
                    value='<%= themeDisplay.translate("button.view.article")%>'
                    href="<%=ohjaajaArticleShortUrlPrefix+comment.artikkeliErc()%>"
                    target="_blank"
                    cssClass="btn btn-secondary btn-sm"
                />
              </div>
            </td>
          </tr>
          <% } %>
          </tbody>
        </table>

        <c:if test="${commentsPage.maara() > 0}">

          <div class="pagination-bar">
            <nav aria-label="Pagination">
              <ul class="pagination pagination-root">


                <li class="page-item <%if(currentPage == 1) {%>disabled<%}%>">
                  <%if (currentPage > 1) {%>
                  <a class="page-link" aria-label="<%= LanguageUtil.format(request, "previous.page.aria", currentPage-1)%>"
                     role="button" tabindex="0"
                     href="<%= renderResponse.createRenderURL().toString() %>&<%= renderResponse.getNamespace() %>tab=all&<%= renderResponse.getNamespace() %>currentPage=<%= currentPage-1 %>">
                    <liferay-ui:icon icon="angle-left"/>
                  </a>
                  <%} else { %>
                  <div class="page-link">
                    <liferay-ui:icon icon="angle-left"/>
                  </div>
                  <% }%>
                </li>

                <% request.setAttribute("currentPage", currentPage); %>
                <% for (int pageNum = 1; pageNum <= commentsPage.sivuja(); pageNum++) {
                  request.setAttribute("pageNum", pageNum);
                %>
                <c:choose>
                  <c:when test="${pageNum == currentPage}">
                    <li class="page-item active">
                      <a class="page-link" aria-label="<%= LanguageUtil.format(request, "page.number.aria", pageNum)%>"
                         tabindex="0">${pageNum}</a>
                    </li>
                  </c:when>
                  <c:otherwise>
                    <li class="page-item">
                      <a class="page-link"
                         href="<%= renderResponse.createRenderURL().toString() %>&<%= renderResponse.getNamespace() %>tab=all&<%= renderResponse.getNamespace() %>currentPage=<%= pageNum %>"
                         aria-label="Go to page, ${pageNum}" tabindex="0">${pageNum}</a>
                    </li>
                  </c:otherwise>
                </c:choose>
                <% } %>
                <li class="page-item <%if(currentPage == commentsPage.sivuja()) {%>disabled<%}%>">
                  <%if (currentPage < commentsPage.sivuja()) {%>
                  <a class="page-link" aria-label="<%= LanguageUtil.format(request, "next.page.aria", currentPage+1)%>"
                     role="button" tabindex="0"
                     href="<%= renderResponse.createRenderURL().toString() %>&<%= renderResponse.getNamespace() %>tab=all&<%= renderResponse.getNamespace() %>currentPage=<%= currentPage+1 %>">
                    <liferay-ui:icon icon="angle-right"/>
                  </a>
                  <%} else { %>
                  <div class="page-link">
                    <liferay-ui:icon icon="angle-right"/>
                  </div>
                  <% }%>
                </li>
              </ul>
            </nav>
          </div>

        </c:if>
      </c:otherwise>
    </c:choose>
    <% } %>
  </div>
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

