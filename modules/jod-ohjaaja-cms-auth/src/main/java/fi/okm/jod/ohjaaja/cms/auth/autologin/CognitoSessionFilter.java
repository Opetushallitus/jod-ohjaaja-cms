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
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.Portal;
import fi.okm.jod.ohjaaja.cms.auth.configuration.CognitoAutoLoginConfiguration;
import fi.okm.jod.ohjaaja.cms.auth.configuration.CognitoAutoLoginConfigurationUtil;
import fi.okm.jod.ohjaaja.cms.auth.jwt.OidcTokenVerifier;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;

/**
 * Ends the Liferay session when the ALB starts sending the OIDC headers of another Cognito user.
 *
 * <p>Liferay runs the AutoLogin only for requests without a signed in user, so without this filter
 * a session started by {@link CognitoAutoLogin} would stay with the previous user after the ALB
 * session has been re-authenticated as someone else.
 *
 * <p>The Cognito subject is bound to the session after a login by {@link CognitoAutoLogin}.
 * Sessions without a bound subject, such as break-glass logins without the OIDC headers, are not
 * checked. The session is ended only when the headers are present, valid and of another subject, so
 * missing or forged headers cannot end a session.
 */
@Component(
    configurationPid = CognitoAutoLoginConfiguration.PID,
    property = {
      "before-filter=Auto Login Filter",
      "dispatcher=FORWARD",
      "dispatcher=REQUEST",
      "servlet-context-name=",
      "servlet-filter-name=Cognito Session Filter",
      "url-pattern=/*"
    },
    service = Filter.class)
public class CognitoSessionFilter implements Filter {

  static final String SESSION_SUBJECT_ATTRIBUTE = CognitoSessionFilter.class.getName() + "#SUBJECT";

  private static final Log log = LogFactoryUtil.getLog(CognitoSessionFilter.class);

  @Reference private Portal portal;

  private volatile OidcTokenVerifier verifier;

  @Activate
  @Modified
  protected void activate(Map<String, Object> properties) {
    var configuration =
        ConfigurableUtil.createConfigurable(CognitoAutoLoginConfiguration.class, properties);
    verifier =
        CognitoAutoLoginConfigurationUtil.isEnabledAndComplete(configuration)
            ? new OidcTokenVerifier(configuration)
            : null;
  }

  @Override
  public void doFilter(
      ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
      throws IOException, ServletException {
    var currentVerifier = verifier;
    if (currentVerifier == null
        || !(servletRequest instanceof HttpServletRequest httpServletRequest)
        || !(servletResponse instanceof HttpServletResponse httpServletResponse)) {
      filterChain.doFilter(servletRequest, servletResponse);
      return;
    }
    if (endSessionOfOtherSubject(httpServletRequest, httpServletResponse, currentVerifier)) {
      return;
    }
    try {
      filterChain.doFilter(servletRequest, servletResponse);
    } finally {
      bindLoginSubject(httpServletRequest);
    }
  }

  private boolean endSessionOfOtherSubject(
      HttpServletRequest httpServletRequest,
      HttpServletResponse httpServletResponse,
      OidcTokenVerifier currentVerifier)
      throws IOException {
    var session = httpServletRequest.getSession(false);
    if (session == null
        || !(session.getAttribute(SESSION_SUBJECT_ATTRIBUTE) instanceof String sessionSubject)) {
      return false;
    }
    var oidcData = httpServletRequest.getHeader(CognitoAutoLogin.OIDC_DATA_HEADER);
    // Verifying the headers on every request is not needed while the subject stays the same
    if (OidcTokenVerifier.readUnverifiedSubject(oidcData)
        .map(sessionSubject::equals)
        .orElse(true)) {
      return false;
    }
    var identity =
        currentVerifier
            .verify(
                oidcData, httpServletRequest.getHeader(CognitoAutoLogin.OIDC_ACCESS_TOKEN_HEADER))
            .orElse(null);
    if (identity == null || identity.sub().equals(sessionSubject)) {
      return false;
    }

    log.warn(
        "Cognito user changed from "
            + sessionSubject
            + " to "
            + identity.sub()
            + ", ending the Liferay session");
    session.invalidate();
    if ("GET".equals(httpServletRequest.getMethod())
        || "HEAD".equals(httpServletRequest.getMethod())) {
      // The AutoLogin signs in the new user on the next request
      httpServletResponse.sendRedirect(portal.getCurrentURL(httpServletRequest));
    } else {
      httpServletResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }
    return true;
  }

  private void bindLoginSubject(HttpServletRequest httpServletRequest) {
    if (!(httpServletRequest.getAttribute(CognitoAutoLogin.LOGIN_SUBJECT_ATTRIBUTE)
        instanceof String loginSubject)) {
      return;
    }
    httpServletRequest.removeAttribute(CognitoAutoLogin.LOGIN_SUBJECT_ATTRIBUTE);
    var session = httpServletRequest.getSession(false);
    if (session == null) {
      return;
    }
    try {
      session.setAttribute(SESSION_SUBJECT_ATTRIBUTE, loginSubject);
    } catch (IllegalStateException e) {
      log.debug("Session was invalidated before binding Cognito user " + loginSubject);
    }
  }
}
