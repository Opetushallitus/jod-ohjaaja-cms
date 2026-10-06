/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.auth.autologin;

import com.liferay.portal.configuration.metatype.bnd.util.ConfigurableUtil;
import com.liferay.portal.kernel.language.LanguageUtil;
import com.liferay.portal.kernel.util.HtmlUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.ResourceBundleUtil;
import com.liferay.portal.kernel.util.StringUtil;
import fi.okm.jod.ohjaaja.cms.auth.configuration.CognitoAutoLoginConfiguration;
import fi.okm.jod.ohjaaja.cms.auth.configuration.CognitoAutoLoginConfigurationUtil;
import fi.okm.jod.ohjaaja.cms.auth.user.LoginResult.DenialReason;
import jakarta.servlet.Servlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;

/**
 * Tells a user authenticated by Cognito why the CMS cannot be used, and offers a logout from the
 * ALB and Cognito sessions so that the user can sign in with another account.
 *
 * <p>The page is served outside the paths of Liferay's AutoLogin filter, so it does not trigger the
 * AutoLogin again. It shows nothing about the Cognito identity.
 */
@Component(
    configurationPid = CognitoAutoLoginConfiguration.PID,
    property = {
      "osgi.http.whiteboard.servlet.name=fi.okm.jod.ohjaaja.cms.auth.autologin.AccessDeniedServlet",
      "osgi.http.whiteboard.servlet.pattern=" + AccessDeniedServlet.PATH
    },
    service = Servlet.class)
public class AccessDeniedServlet extends HttpServlet {

  /** Path of the page, relative to the module path ({@code /o}). */
  static final String PATH = "/jod-cms-auth/access-denied";

  static final String REASON_PARAMETER = "reason";

  private static final long serialVersionUID = 1L;

  @Reference private transient Portal portal;

  private transient volatile String logoutUrl;

  @Activate
  @Modified
  protected void activate(Map<String, Object> properties) {
    var configuration =
        ConfigurableUtil.createConfigurable(CognitoAutoLoginConfiguration.class, properties);
    logoutUrl =
        CognitoAutoLoginConfigurationUtil.isEnabledAndComplete(configuration)
            ? configuration.logoutUrl()
            : null;
  }

  static String toParameterValue(DenialReason reason) {
    return StringUtil.toLowerCase(reason.name()).replace('_', '-');
  }

  /** Unknown values fall back to {@link DenialReason#NO_ACCESS}. */
  static DenialReason fromParameterValue(String value) {
    return Arrays.stream(DenialReason.values())
        .filter(reason -> toParameterValue(reason).equals(value))
        .findFirst()
        .orElse(DenialReason.NO_ACCESS);
  }

  @Override
  protected void doGet(
      HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
      throws IOException {
    var locale = portal.getLocale(httpServletRequest);
    var resourceBundle = ResourceBundleUtil.getBundle("content.Language", locale, getClass());
    var reason = fromParameterValue(httpServletRequest.getParameter(REASON_PARAMETER));
    var currentLogoutUrl = logoutUrl;

    var html = new StringBuilder();
    html.append("<!DOCTYPE html><html lang=\"")
        .append(HtmlUtil.escapeAttribute(locale.getLanguage()))
        .append("\"><head><meta charset=\"UTF-8\">")
        .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        .append("<title>")
        .append(HtmlUtil.escape(LanguageUtil.get(resourceBundle, "access-denied.title")))
        .append("</title><style>")
        .append("body{margin:0;font-family:system-ui,sans-serif;background:#f5f5f5;color:#1a1a1a}")
        .append("main{max-width:32rem;margin:15vh auto 0;padding:2rem;background:#fff;")
        .append("border-radius:8px;box-shadow:0 1px 4px rgba(0,0,0,.15)}")
        .append("h1{margin-top:0;font-size:1.5rem}p{line-height:1.5}")
        .append("a.button{display:inline-block;margin-top:1rem;padding:.6rem 1.2rem;")
        .append("background:#006dd2;color:#fff;border-radius:4px;text-decoration:none}")
        .append("</style></head><body><main><h1>")
        .append(HtmlUtil.escape(LanguageUtil.get(resourceBundle, "access-denied.title")))
        .append("</h1><p>")
        .append(
            HtmlUtil.escape(
                LanguageUtil.get(resourceBundle, "access-denied." + toParameterValue(reason))))
        .append("</p>");
    if (currentLogoutUrl != null) {
      html.append("<p>")
          .append(HtmlUtil.escape(LanguageUtil.get(resourceBundle, "access-denied.logout-hint")))
          .append("</p><a class=\"button\" href=\"")
          .append(HtmlUtil.escapeHREF(currentLogoutUrl))
          .append("\">")
          .append(HtmlUtil.escape(LanguageUtil.get(resourceBundle, "access-denied.logout")))
          .append("</a>");
    }
    html.append("</main></body></html>");

    httpServletResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
    httpServletResponse.setContentType("text/html; charset=UTF-8");
    httpServletResponse.setHeader("Cache-Control", "no-store");
    httpServletResponse.getWriter().write(html.toString());
  }
}
