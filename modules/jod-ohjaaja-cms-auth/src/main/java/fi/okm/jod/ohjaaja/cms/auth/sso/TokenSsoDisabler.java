/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.auth.sso;

import com.liferay.portal.configuration.metatype.bnd.util.ConfigurableUtil;
import com.liferay.portal.configuration.module.configuration.ConfigurationProvider;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.module.configuration.ConfigurationException;
import com.liferay.portal.kernel.service.CompanyLocalService;
import com.liferay.portal.kernel.service.PortletPreferencesLocalService;
import com.liferay.portal.kernel.settings.CompanyServiceSettingsLocator;
import com.liferay.portal.kernel.util.HashMapDictionaryBuilder;
import com.liferay.portal.kernel.util.PortletKeys;
import com.liferay.portal.security.sso.token.configuration.TokenConfiguration;
import com.liferay.portal.security.sso.token.constants.TokenConstants;
import fi.okm.jod.ohjaaja.cms.auth.configuration.CognitoAutoLoginConfiguration;
import fi.okm.jod.ohjaaja.cms.auth.configuration.CognitoAutoLoginConfigurationUtil;
import jakarta.portlet.ReadOnlyException;
import jakarta.portlet.ValidatorException;
import java.io.IOException;
import java.util.Dictionary;
import java.util.Map;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;

/**
 * Disables Liferay's Token Based SSO once the Cognito AutoLogin is enabled and configured. Token
 * Based SSO trusts a request header without verifying it, so it must not stay enabled next to the
 * verifying AutoLogin.
 *
 * <p>Token Based SSO settings are read from the system configuration, the company configuration and
 * the company portlet preferences (legacy Instance Settings), the latter taking precedence. All of
 * them are disabled.
 */
@Component(
    configurationPid = CognitoAutoLoginConfiguration.PID,
    immediate = true,
    service = {})
public class TokenSsoDisabler {

  private static final Log log = LogFactoryUtil.getLog(TokenSsoDisabler.class);

  @Reference private CompanyLocalService companyLocalService;
  @Reference private ConfigurationProvider configurationProvider;
  @Reference private PortletPreferencesLocalService portletPreferencesLocalService;

  @Activate
  @Modified
  protected void activate(Map<String, Object> properties) {
    var configuration =
        ConfigurableUtil.createConfigurable(CognitoAutoLoginConfiguration.class, properties);
    if (!CognitoAutoLoginConfigurationUtil.isEnabledAndComplete(configuration)) {
      log.info("Cognito AutoLogin is not enabled, leaving Token Based SSO settings as is");
      return;
    }
    try {
      disableSystemConfiguration();
    } catch (ConfigurationException e) {
      log.error("Disabling Token Based SSO system configuration failed", e);
    }
    companyLocalService.forEachCompanyId(this::disableCompanyConfiguration);
  }

  private void disableSystemConfiguration() throws ConfigurationException {
    var configuration = configurationProvider.getSystemConfiguration(TokenConfiguration.class);
    if (configuration.enabled()) {
      configurationProvider.saveSystemConfiguration(
          TokenConfiguration.class, disabled(configuration));
      log.info("Disabled Token Based SSO system configuration");
    }
  }

  private void disableCompanyConfiguration(long companyId) {
    try {
      var configuration = getCompanyConfiguration(companyId);
      if (!configuration.enabled()) {
        return;
      }
      configurationProvider.saveCompanyConfiguration(
          TokenConfiguration.class, companyId, disabled(configuration));
      resetCompanyPortletPreferences(companyId);
      if (getCompanyConfiguration(companyId).enabled()) {
        log.error(
            "Token Based SSO is still enabled for company "
                + companyId
                + ", disable it in Instance Settings > Security > SSO");
      } else {
        log.info("Disabled Token Based SSO for company " + companyId);
      }
    } catch (ConfigurationException e) {
      log.error("Disabling Token Based SSO for company " + companyId + " failed", e);
    }
  }

  /**
   * Removes the legacy {@code enabled} value of the company portlet preferences, so that the
   * disabled company configuration applies.
   */
  private void resetCompanyPortletPreferences(long companyId) {
    if (portletPreferencesLocalService.fetchPortletPreferences(
            companyId,
            PortletKeys.PREFS_OWNER_TYPE_COMPANY,
            PortletKeys.PREFS_PLID_SHARED,
            TokenConstants.SERVICE_NAME)
        == null) {
      return;
    }
    var preferences =
        portletPreferencesLocalService.getStrictPreferences(
            companyId,
            companyId,
            PortletKeys.PREFS_OWNER_TYPE_COMPANY,
            PortletKeys.PREFS_PLID_SHARED,
            TokenConstants.SERVICE_NAME);
    if (preferences.getValue("enabled", null) == null) {
      return;
    }
    try {
      preferences.reset("enabled");
      preferences.store();
      log.info("Removed legacy Token Based SSO instance setting of company " + companyId);
    } catch (IOException | ReadOnlyException | ValidatorException e) {
      log.error(
          "Removing legacy Token Based SSO instance setting of company " + companyId + " failed",
          e);
    }
  }

  /** Reads the configuration the same way Liferay's TokenAutoLogin does. */
  private TokenConfiguration getCompanyConfiguration(long companyId) throws ConfigurationException {
    return configurationProvider.getConfiguration(
        TokenConfiguration.class,
        new CompanyServiceSettingsLocator(companyId, TokenConstants.SERVICE_NAME));
  }

  private static Dictionary<String, Object> disabled(TokenConfiguration configuration) {
    return HashMapDictionaryBuilder.<String, Object>put(
            "authenticationCookies", configuration.authenticationCookies())
        .put("enabled", false)
        .put("importFromLDAP", configuration.importFromLDAP())
        .put("logoutRedirectURL", configuration.logoutRedirectURL())
        .put("tokenLocation", configuration.tokenLocation())
        .put("userTokenName", configuration.userTokenName())
        .build();
  }
}
