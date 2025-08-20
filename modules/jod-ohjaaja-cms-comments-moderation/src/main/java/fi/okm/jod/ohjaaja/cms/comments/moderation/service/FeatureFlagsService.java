/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.comments.moderation.service;

import static fi.okm.jod.ohjaaja.cms.comments.moderation.util.TokenUtil.getToken;

import fi.okm.jod.ohjaaja.cms.comments.moderation.client.Feature;
import fi.okm.jod.ohjaaja.cms.comments.moderation.client.FeaturesApiClient;
import fi.okm.jod.ohjaaja.cms.comments.moderation.dto.FeatureFlagDto;
import java.util.List;
import javax.portlet.PortletRequest;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = FeatureFlagsService.class)
public class FeatureFlagsService {
  @Reference private FeaturesApiClient featuresApiClient;

  public List<FeatureFlagDto> getFeatureFlags(PortletRequest portletRequest) {
    return featuresApiClient.getFeatureFlags(getToken(portletRequest));
  }

  public boolean isFeatureEnabled(PortletRequest portletRequest, Feature feature) {
    var featureFlags = getFeatureFlags(portletRequest);
    return featureFlags.stream()
        .filter(flag -> flag.feature().equals(feature))
        .findFirst()
        .map(FeatureFlagDto::enabled)
        .orElse(false);
  }

  public void updateFeatureFlag(PortletRequest portletRequest, Feature feature, boolean enabled) {
    featuresApiClient.setFeatureFlag(feature, enabled, getToken(portletRequest));
  }
}
