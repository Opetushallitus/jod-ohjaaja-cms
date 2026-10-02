/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.auth.configuration;

import com.liferay.portal.kernel.util.Validator;

public final class CognitoAutoLoginConfigurationUtil {

  private CognitoAutoLoginConfigurationUtil() {}

  /**
   * Returns true when the AutoLogin is enabled and every value needed for token verification is
   * set. Unresolved environment variable placeholders ("$[env:...]") count as missing.
   */
  public static boolean isEnabledAndComplete(CognitoAutoLoginConfiguration configuration) {
    return configuration.enabled()
        && isSet(configuration.region())
        && isSet(configuration.userPoolId())
        && isSet(configuration.clientId())
        && isSet(configuration.albArn())
        && isSet(configuration.logoutUrl());
  }

  private static boolean isSet(String value) {
    return Validator.isNotNull(value) && !value.startsWith("$[");
  }
}
