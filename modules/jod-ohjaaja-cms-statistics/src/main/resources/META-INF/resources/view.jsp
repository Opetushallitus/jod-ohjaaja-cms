<%@ page import="com.liferay.portal.kernel.language.LanguageUtil" %>
<%@ page import="com.liferay.portal.kernel.servlet.SessionErrors" %>
<%@ page import="com.liferay.portal.kernel.util.HtmlUtil" %>
<%@ page import="com.liferay.portal.kernel.util.HttpComponentsUtil" %>
<%@ page import="fi.okm.jod.ohjaaja.cms.statistics.view.ArticleRow" %>
<%@ page import="fi.okm.jod.ohjaaja.cms.statistics.view.DistributionRow" %>
<%@ page import="fi.okm.jod.ohjaaja.cms.statistics.view.StatisticsView" %>
<%@ page import="jakarta.portlet.PortletRequest" %>
<%@ page import="java.text.NumberFormat" %>
<%@ page import="java.time.LocalDate" %>
<%@ page import="java.time.format.DateTimeFormatter" %>
<%@ page import="java.time.format.FormatStyle" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@include file="init.jsp" %>
<%
  StatisticsView statistics = (StatisticsView) request.getAttribute("statistics");
  LocalDate alku = (LocalDate) request.getAttribute("alku");
  LocalDate loppu = (LocalDate) request.getAttribute("loppu");
  int top = (Integer) request.getAttribute("top");
  List<Integer> topOptions = (List<Integer>) request.getAttribute("topOptions");
  PortletRequest portletRequest = (PortletRequest) request.getAttribute("jakarta.portlet.request");

  NumberFormat numberFormat = NumberFormat.getIntegerInstance(themeDisplay.getLocale());
  DateTimeFormatter dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(themeDisplay.getLocale());
  String namespace = renderResponse.getNamespace();
%>

<%
  String statisticsURLString = renderResponse.createRenderURL().toString();
  Map<String, String[]> statisticsURLParameters =
      HttpComponentsUtil.getParameterMap(HttpComponentsUtil.getQueryString(statisticsURLString));
%>

<div class="container bg-white mt-4 p-5">
  <h2>
    <liferay-ui:message key="statistics.page.title"/>
  </h2>
  <p>
    <liferay-ui:message key="statistics.page.description"/>
  </p>

  <% if (!SessionErrors.isEmpty(portletRequest)) { %>
  <div class="alert alert-danger">
    <ul class="mb-0">
      <c:forEach var="error" items="<%=SessionErrors.keySet(portletRequest)%>">
        <li><liferay-ui:message key="${error}"/></li>
      </c:forEach>
    </ul>
  </div>
  <% } %>

  <% if (statistics != null) { %>
  <section class="mb-5">
    <h3><liferay-ui:message key="statistics.section.users"/></h3>
    <p class="text-secondary"><liferay-ui:message key="statistics.section.users.description"/></p>

    <div class="row mb-4">
      <div class="col-md-4">
        <div class="card h-100">
          <div class="card-body">
            <div class="text-secondary"><liferay-ui:message key="statistics.registered.users"/></div>
            <div style="font-size: 2.5rem; font-weight: 600; line-height: 1.2;">
              <%=numberFormat.format(statistics.registeredUsers())%>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="row">
      <%
        Map<String, List<DistributionRow>> distributions = new java.util.LinkedHashMap<>();
        distributions.put("statistics.workplaces", statistics.workplaces());
        distributions.put("statistics.interests", statistics.interests());
        for (Map.Entry<String, List<DistributionRow>> distribution : distributions.entrySet()) {
      %>
      <div class="col-lg-6 mb-4">
        <h4><liferay-ui:message key="<%=distribution.getKey()%>"/></h4>
        <% if (distribution.getValue().isEmpty()) { %>
        <div class="alert alert-info"><liferay-ui:message key="statistics.no.data"/></div>
        <% } else { %>
        <table class="table table-sm">
          <thead>
          <tr>
            <th><liferay-ui:message key="statistics.column.name"/></th>
            <th class="text-right"><liferay-ui:message key="statistics.column.count"/></th>
            <th class="text-right"><liferay-ui:message key="statistics.column.share"/></th>
            <th style="width: 35%"></th>
          </tr>
          </thead>
          <tbody>
          <% for (DistributionRow row : distribution.getValue()) { %>
          <tr>
            <td><%=HtmlUtil.escape(row.label())%></td>
            <td class="text-right"><%=numberFormat.format(row.count())%></td>
            <td class="text-right"><%=row.percent()%>&nbsp;%</td>
            <td class="align-middle">
              <progress value="<%=row.percent()%>" max="100" style="height: 1.1rem;"><%=row.percent()%></progress>
            </td>
          </tr>
          <% } %>
          </tbody>
        </table>
        <% } %>
      </div>
      <% } %>
    </div>
  </section>
  <% } %>

  <section>
    <h3><liferay-ui:message key="statistics.section.articles"/></h3>
    <p class="text-secondary"><liferay-ui:message key="statistics.section.articles.description"/></p>

    <form action="<%=HtmlUtil.escapeAttribute(statisticsURLString)%>" method="get"
          class="bg-light rounded p-3 mb-3">
      <% for (Map.Entry<String, String[]> parameter : statisticsURLParameters.entrySet()) {
           for (String value : parameter.getValue()) { %>
      <input type="hidden" name="<%=HtmlUtil.escapeAttribute(parameter.getKey())%>" value="<%=HtmlUtil.escapeAttribute(value)%>"/>
      <% } } %>
      <div class="d-flex flex-wrap align-items-end" style="gap: 1rem;">
        <div>
          <label for="<%=namespace%>alku"><liferay-ui:message key="statistics.filter.start"/></label>
          <input class="form-control" type="date" id="<%=namespace%>alku" name="<%=namespace%>alku"
                 value="<%=alku != null ? alku.toString() : ""%>"/>
        </div>
        <div>
          <label for="<%=namespace%>loppu"><liferay-ui:message key="statistics.filter.end"/></label>
          <input class="form-control" type="date" id="<%=namespace%>loppu" name="<%=namespace%>loppu"
                 value="<%=loppu != null ? loppu.toString() : ""%>"/>
        </div>
        <div>
          <label for="<%=namespace%>top"><liferay-ui:message key="statistics.filter.top"/></label>
          <select class="form-control" id="<%=namespace%>top" name="<%=namespace%>top">
            <% for (Integer option : topOptions) { %>
            <option value="<%=option%>" <%=option == top ? "selected" : ""%>><%=option%></option>
            <% } %>
          </select>
        </div>
        <div class="d-flex" style="gap: 0.5rem;">
          <button type="submit" class="btn btn-primary"><liferay-ui:message key="statistics.button.apply"/></button>
          <a href="<%=HtmlUtil.escapeHREF(statisticsURLString)%>" class="btn btn-secondary"><liferay-ui:message key="statistics.button.clear"/></a>
        </div>
      </div>
    </form>

    <% if (statistics != null) { %>
    <p>
      <strong><liferay-ui:message key="statistics.period"/>:</strong>
      <% if (alku == null && loppu == null) { %>
      <liferay-ui:message key="statistics.period.all"/>
      <% } else { %>
      <%=alku != null ? dateFormatter.format(alku) : ""%> &ndash; <%=loppu != null ? dateFormatter.format(loppu) : ""%>
      <% } %>
    </p>

    <div class="row">
      <%
        Map<String, List<ArticleRow>> articleLists = new java.util.LinkedHashMap<>();
        articleLists.put("statistics.most.favorited", statistics.mostFavorited());
        articleLists.put("statistics.most.commented", statistics.mostCommented());
        articleLists.put("statistics.most.viewed", statistics.mostViewed());
        for (Map.Entry<String, List<ArticleRow>> articleList : articleLists.entrySet()) {
      %>
      <div class="col-lg-4 mb-4">
        <h4><liferay-ui:message key="<%=articleList.getKey()%>"/></h4>
        <% if (articleList.getValue().isEmpty()) { %>
        <div class="alert alert-info"><liferay-ui:message key="statistics.no.data"/></div>
        <% } else { %>
        <table class="table table-sm table-striped">
          <thead>
          <tr>
            <th style="width: 2.5rem">#</th>
            <th><liferay-ui:message key="statistics.column.article"/></th>
            <th class="text-right"><liferay-ui:message key="statistics.column.count"/></th>
          </tr>
          </thead>
          <tbody>
          <% int rank = 1; for (ArticleRow row : articleList.getValue()) { %>
          <tr>
            <td><%=rank++%></td>
            <td style="word-break: break-word;">
              <a href="<%=HtmlUtil.escapeHREF(row.url())%>" target="_blank" rel="noopener noreferrer">
                <%=HtmlUtil.escape(row.title())%>
              </a>
            </td>
            <td class="text-right"><%=numberFormat.format(row.count())%></td>
          </tr>
          <% } %>
          </tbody>
        </table>
        <% } %>
      </div>
      <% } %>
    </div>
    <% } %>
  </section>
</div>
