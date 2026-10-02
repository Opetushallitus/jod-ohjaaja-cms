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
import com.liferay.portal.kernel.module.framework.ModuleServiceLifecycle;
import java.util.List;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * All guest-accessible APIs in one place. Policies are overwritten on every startup, so changes
 * made in the Service Access Policy admin UI do not survive a restart.
 */
@Component(
    immediate = true,
    service = {})
public class PublicAccessPolicyInitializer {

  private static final Log log = LogFactoryUtil.getLog(PublicAccessPolicyInitializer.class);

  private static final String STRUCTURED_CONTENT_RESOURCE =
      "com.liferay.headless.delivery.internal.resource.v1_0.StructuredContentResourceImpl";

  // Class names as strings because the REST applications live in bundles that depend on this one
  private static final List<Policy> POLICIES =
      List.of(
          // Keep in sync with jod-ohjaaja-ui: any headless-delivery endpoint not listed here
          // returns 403 for anonymous users
          new Policy(
              "HEADLESS_ACCESS",
              String.join(
                  "\n",
                  // GET /headless-delivery/v1.0/sites/{siteId}/structured-contents
                  STRUCTURED_CONTENT_RESOURCE + "#getSiteStructuredContentsPage",
                  // GET /headless-delivery/v1.0/structured-contents/{structuredContentId}
                  STRUCTURED_CONTENT_RESOURCE + "#getStructuredContent"),
              "Public access to headless-delivery for jod-ohjaaja-ui"),
          new Policy(
              "JOD_OHJAAJA_NAVIGATION",
              "fi.okm.jod.ohjaaja.cms.navigation.rest.application.NavigationRestApplication#*",
              "Public access JOD OHJAAJA NAVIGATION"),
          new Policy(
              "JOD_OHJAAJA_TAGS",
              "fi.okm.jod.ohjaaja.cms.tags.rest.application.TagsRestApplication#*",
              "Public access JOD OHJAAJA TAGS"));

  @Reference private ServiceAccessPolicyInitializer serviceAccessPolicyInitializer;

  // Default company and its admin user must exist before policies can be added
  @Reference(target = ModuleServiceLifecycle.PORTAL_INITIALIZED)
  private ModuleServiceLifecycle portalInitialized;

  @Activate
  protected void activate() {
    for (var policy : POLICIES) {
      try {
        serviceAccessPolicyInitializer.initPublicServiceAccessPolicy(
            policy.name(), policy.allowedServiceSignatures(), policy.title());
      } catch (PortalException | RuntimeException e) {
        log.error("Failed to initialize service access policy " + policy.name(), e);
      }
    }
  }

  private record Policy(String name, String allowedServiceSignatures, String title) {}
}
