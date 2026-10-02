/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.auth.logout;

import com.liferay.portal.configuration.metatype.bnd.util.ConfigurableUtil;
import com.liferay.portal.kernel.events.Action;
import com.liferay.portal.kernel.events.ActionException;
import com.liferay.portal.kernel.events.LifecycleAction;
import fi.okm.jod.ohjaaja.cms.auth.configuration.CognitoAutoLoginConfiguration;
import fi.okm.jod.ohjaaja.cms.auth.configuration.CognitoAutoLoginConfigurationUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;

/**
 * Ends the ALB and Cognito sessions when a user logs out of Liferay. Otherwise the ALB would keep
 * sending valid OIDC headers and {@code CognitoAutoLogin} would log the user straight back in.
 *
 * <p>On an explicit logout the user is redirected to the ALB logout endpoint (jod-infra logout
 * Lambda), which expires the ALB session cookies and redirects to the Cognito logout endpoint.
 * Replaces the logout handling of Token Based SSO, which is disabled by {@code TokenSsoDisabler}.
 */
@Component(
    configurationPid = CognitoAutoLoginConfiguration.PID,
    property = "key=logout.events.post",
    service = LifecycleAction.class)
public class CognitoLogoutAction extends Action {

  private volatile CognitoAutoLoginConfiguration configuration;

  @Activate
  @Modified
  protected void activate(Map<String, Object> properties) {
    configuration =
        ConfigurableUtil.createConfigurable(CognitoAutoLoginConfiguration.class, properties);
  }

  @Override
  public void run(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
      throws ActionException {
    var currentConfiguration = configuration;
    if (currentConfiguration == null
        || !CognitoAutoLoginConfigurationUtil.isEnabledAndComplete(currentConfiguration)) {
      return;
    }

    var pathInfo = httpServletRequest.getPathInfo();
    if (pathInfo == null || !pathInfo.contains("/portal/logout")) {
      return;
    }

    try {
      httpServletResponse.sendRedirect(currentConfiguration.logoutUrl());
    } catch (IOException e) {
      throw new ActionException(e);
    }
  }
}
