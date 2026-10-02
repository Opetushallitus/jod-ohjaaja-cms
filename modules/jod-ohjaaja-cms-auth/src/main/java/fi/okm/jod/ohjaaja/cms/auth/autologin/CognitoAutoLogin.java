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
import com.liferay.portal.kernel.security.auto.login.AutoLogin;
import com.liferay.portal.kernel.security.auto.login.BaseAutoLogin;
import com.liferay.portal.kernel.util.Portal;
import fi.okm.jod.ohjaaja.cms.auth.configuration.CognitoAutoLoginConfiguration;
import fi.okm.jod.ohjaaja.cms.auth.configuration.CognitoAutoLoginConfigurationUtil;
import fi.okm.jod.ohjaaja.cms.auth.jwt.OidcTokenVerifier;
import fi.okm.jod.ohjaaja.cms.auth.user.CognitoUserLogin;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;

/**
 * Logs in users authenticated by the AWS Application Load Balancer against Cognito.
 *
 * <p>Both OIDC headers are verified before they are trusted. Resolving the Liferay user and its
 * roles is delegated to {@link CognitoUserLogin}. The Cognito subject of the login is handed to
 * {@link CognitoSessionFilter}, which binds it to the Liferay session.
 */
@Component(configurationPid = CognitoAutoLoginConfiguration.PID, service = AutoLogin.class)
public class CognitoAutoLogin extends BaseAutoLogin {

  static final String OIDC_DATA_HEADER = "x-amzn-oidc-data";
  static final String OIDC_ACCESS_TOKEN_HEADER = "x-amzn-oidc-accesstoken";
  static final String LOGIN_SUBJECT_ATTRIBUTE = CognitoAutoLogin.class.getName() + "#SUBJECT";

  private static final Log log = LogFactoryUtil.getLog(CognitoAutoLogin.class);

  @Reference private Portal portal;
  @Reference private CognitoUserLogin cognitoUserLogin;

  private volatile OidcTokenVerifier verifier;

  @Activate
  @Modified
  protected void activate(Map<String, Object> properties) {
    var configuration =
        ConfigurableUtil.createConfigurable(CognitoAutoLoginConfiguration.class, properties);
    if (!CognitoAutoLoginConfigurationUtil.isEnabledAndComplete(configuration)) {
      if (configuration.enabled()) {
        log.error("Cognito AutoLogin is enabled but not fully configured, it stays disabled");
      }
      verifier = null;
      return;
    }
    verifier = new OidcTokenVerifier(configuration);
    log.info("Cognito AutoLogin enabled");
  }

  @Override
  protected String[] doLogin(
      HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
      throws Exception {
    var currentVerifier = verifier;
    if (currentVerifier == null) {
      return null;
    }
    var identity =
        currentVerifier
            .verify(
                httpServletRequest.getHeader(OIDC_DATA_HEADER),
                httpServletRequest.getHeader(OIDC_ACCESS_TOKEN_HEADER))
            .orElse(null);
    if (identity == null) {
      return null;
    }
    var user =
        cognitoUserLogin.login(portal.getCompanyId(httpServletRequest), identity).orElse(null);
    if (user == null) {
      return null;
    }
    // Liferay renews the session after the AutoLogin, so the subject cannot be stored in the
    // session here
    httpServletRequest.setAttribute(LOGIN_SUBJECT_ATTRIBUTE, identity.sub());
    return new String[] {
      String.valueOf(user.getUserId()), user.getPassword(), Boolean.TRUE.toString()
    };
  }
}
