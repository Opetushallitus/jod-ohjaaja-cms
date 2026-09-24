/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.statistics.util;

import com.liferay.portal.kernel.util.PortalUtil;
import jakarta.portlet.PortletRequest;

public final class TokenUtil {
  private TokenUtil() {}

  public static String getToken(PortletRequest portletRequest) {
    return PortalUtil.getHttpServletRequest(portletRequest).getHeader("x-amzn-oidc-accesstoken");
  }
}
