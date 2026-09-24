/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.statistics.portlet;

import com.liferay.portal.kernel.portlet.bridges.mvc.MVCPortlet;
import com.liferay.portal.kernel.servlet.SessionErrors;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.WebKeys;
import fi.okm.jod.ohjaaja.cms.statistics.client.exception.StatisticsApiException;
import fi.okm.jod.ohjaaja.cms.statistics.constants.StatisticsPortletKeys;
import fi.okm.jod.ohjaaja.cms.statistics.service.StatisticsService;
import jakarta.portlet.Portlet;
import jakarta.portlet.PortletException;
import jakarta.portlet.RenderRequest;
import jakarta.portlet.RenderResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(
    property = {
      "com.liferay.portlet.css-class-wrapper=portlet-statistics",
      "com.liferay.portlet.display-category=category.moderation",
      "com.liferay.portlet.preferences-owned-by-group=true",
      "com.liferay.portlet.private-request-attributes=false",
      "com.liferay.portlet.private-session-attributes=false",
      "com.liferay.portlet.remoteable=false",
      "com.liferay.portlet.render-weight=50",
      "com.liferay.portlet.use-default-template=true",
      "jakarta.portlet.display-name=Tilastot",
      "jakarta.portlet.expiration-cache=0",
      "jakarta.portlet.init-param.always-display-default-configuration-icons=true",
      "jakarta.portlet.init-param.view-template=/view.jsp",
      "jakarta.portlet.name=" + StatisticsPortletKeys.STATISTICS,
      "jakarta.portlet.resource-bundle=content.Language",
      "jakarta.portlet.security-role-ref=administrator,power-user",
      "jakarta.portlet.supports.mime-type=text/html",
      "jakarta.portlet.supported-locale=en_US,fi_FI"
    },
    service = Portlet.class)
public class StatisticsPortlet extends MVCPortlet {

  public static final List<Integer> TOP_OPTIONS = List.of(10, 25, 50);

  @Reference private StatisticsService statisticsService;

  @Override
  public void doView(RenderRequest renderRequest, RenderResponse renderResponse)
      throws IOException, PortletException {

    var alku = parseDate(renderRequest, "alku");
    var loppu = parseDate(renderRequest, "loppu");
    var top = ParamUtil.getInteger(renderRequest, "top", TOP_OPTIONS.getFirst());
    if (!TOP_OPTIONS.contains(top)) {
      top = TOP_OPTIONS.getFirst();
    }

    renderRequest.setAttribute("alku", alku);
    renderRequest.setAttribute("loppu", loppu);
    renderRequest.setAttribute("top", top);
    renderRequest.setAttribute("topOptions", TOP_OPTIONS);

    if (alku != null && loppu != null && alku.isAfter(loppu)) {
      SessionErrors.add(renderRequest, "statistics.error.invalid.date.range");
    } else {
      var themeDisplay = (ThemeDisplay) renderRequest.getAttribute(WebKeys.THEME_DISPLAY);
      var locale = themeDisplay.getLocale();
      try {
        renderRequest.setAttribute(
            "statistics",
            statisticsService.getStatistics(
                renderRequest,
                getPortletConfig().getResourceBundle(locale),
                locale,
                alku,
                loppu,
                top));
      } catch (StatisticsApiException e) {
        SessionErrors.add(renderRequest, "statistics.error.fetching.statistics");
      }
    }

    super.doView(renderRequest, renderResponse);
  }

  private static LocalDate parseDate(RenderRequest renderRequest, String name) {
    var value = ParamUtil.getString(renderRequest, name);
    if (value.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException e) {
      SessionErrors.add(renderRequest, "statistics.error.invalid.date");
      return null;
    }
  }
}
