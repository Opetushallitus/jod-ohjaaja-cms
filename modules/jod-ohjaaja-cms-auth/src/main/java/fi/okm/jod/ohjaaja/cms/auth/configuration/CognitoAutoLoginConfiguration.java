/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.auth.configuration;

import aQute.bnd.annotation.metatype.Meta;
import com.liferay.portal.configuration.metatype.annotations.ExtendedObjectClassDefinition;

/**
 * Configuration for the AutoLogin that trusts the OIDC headers added by the AWS Application Load
 * Balancer (Cognito authentication).
 */
@ExtendedObjectClassDefinition(category = "sso", scope = ExtendedObjectClassDefinition.Scope.SYSTEM)
@Meta.OCD(id = CognitoAutoLoginConfiguration.PID, name = "JOD Cognito AutoLogin")
public interface CognitoAutoLoginConfiguration {

  String PID = "fi.okm.jod.ohjaaja.cms.auth.configuration.CognitoAutoLoginConfiguration";

  @Meta.AD(deflt = "false", name = "enabled", required = false)
  boolean enabled();

  @Meta.AD(deflt = "eu-west-1", name = "region", required = false)
  String region();

  @Meta.AD(deflt = "", name = "user-pool-id", required = false)
  String userPoolId();

  @Meta.AD(deflt = "", name = "client-id", required = false)
  String clientId();

  @Meta.AD(
      deflt = "",
      description = "ARN of the load balancer, compared to the signer field of x-amzn-oidc-data",
      name = "alb-arn",
      required = false)
  String albArn();

  @Meta.AD(deflt = "60", name = "clock-skew-seconds", required = false)
  int clockSkewSeconds();

  @Meta.AD(
      deflt = "",
      description =
          "ALB logout endpoint that expires the ALB session cookies and redirects to Cognito"
              + " logout",
      name = "logout-url",
      required = false)
  String logoutUrl();
}
