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
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.security.service.access.policy.service.SAPEntryLocalService;
import java.util.HashMap;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(immediate = true, service = ServiceAccessPolicyInitializer.class)
public class ServiceAccessPolicyInitializerImpl implements ServiceAccessPolicyInitializer {

  private static final Log log = LogFactoryUtil.getLog(ServiceAccessPolicyInitializerImpl.class);

  @Reference private SAPEntryLocalService sapEntryLocalService;

  @Override
  public void initPublicServiceAccessPolicy(
      String name, String allowedServiceSignatures, String title) throws PortalException {
    var companyId = PortalUtil.getDefaultCompanyId();
    // Liferay requires a title in the portal default locale (fi_FI in our environments)
    var titleMap = new HashMap<Locale, String>();
    titleMap.put(LocaleUtil.US, title);
    titleMap.put(LocaleUtil.getDefault(), title);
    var sapEntry = sapEntryLocalService.fetchSAPEntry(companyId, name);

    if (sapEntry == null) {
      try {
        sapEntryLocalService.addSAPEntry(
            AdminUtil.getAdminUser(companyId).getUserId(),
            allowedServiceSignatures,
            true,
            true,
            name,
            titleMap,
            new ServiceContext());
        log.info("Added service access policy " + name);
        return;
      } catch (PortalException | RuntimeException e) {
        // Another node starting at the same time may have added it first; if so, verify that one
        sapEntry = sapEntryLocalService.fetchSAPEntry(companyId, name);
        if (sapEntry == null) {
          throw e;
        }
      }
    }

    if (sapEntry.isDefaultSAPEntry()
        && sapEntry.isEnabled()
        && toSignatureSet(sapEntry.getAllowedServiceSignatures())
            .equals(toSignatureSet(allowedServiceSignatures))) {
      return;
    }

    sapEntryLocalService.updateSAPEntry(
        sapEntry.getSapEntryId(),
        allowedServiceSignatures,
        true,
        true,
        name,
        titleMap,
        new ServiceContext());
    log.warn(
        "Service access policy "
            + name
            + " differed from the code and was overwritten. Previous signatures: "
            + sapEntry.getAllowedServiceSignatures());
  }

  /** Policies edited in the UI may use different line endings, ordering or blank lines. */
  private static Set<String> toSignatureSet(String allowedServiceSignatures) {
    if (allowedServiceSignatures == null) {
      return Set.of();
    }
    return allowedServiceSignatures
        .lines()
        .map(String::strip)
        .filter(line -> !line.isEmpty())
        .collect(Collectors.toSet());
  }
}
