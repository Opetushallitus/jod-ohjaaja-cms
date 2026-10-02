/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.util;

import com.liferay.portal.kernel.exception.PortalException;

public interface ServiceAccessPolicyInitializer {

  /**
   * Makes sure an enabled default (guest-accessible) Service Access Policy exists with the given
   * name and allowed service signatures. An existing policy with the same name is overwritten to
   * match.
   *
   * @param name SAP name, e.g. {@code JOD_OHJAAJA_NAVIGATION}
   * @param allowedServiceSignatures e.g. {@code fi.okm.Foo#*}, one signature per line
   * @param title English title shown in the Service Access Policy admin UI
   */
  void initPublicServiceAccessPolicy(String name, String allowedServiceSignatures, String title)
      throws PortalException;
}
