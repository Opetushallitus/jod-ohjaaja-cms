/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.comments.moderation.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.PropsUtil;
import fi.okm.jod.ohjaaja.cms.comments.moderation.client.exception.FeaturesApiException;
import fi.okm.jod.ohjaaja.cms.comments.moderation.dto.FeatureFlagDto;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.osgi.service.component.annotations.Component;

@Component(service = FeaturesApiClient.class)
public class FeaturesApiClient {
  private static final Log log = LogFactoryUtil.getLog(CommentsModerationApiClient.class);
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

  private static final String API_BASE_PATH = "/internal-api/features";
  private static final String API_URL = PropsUtil.get("ohjaaja.backend.url") + API_BASE_PATH;

  public FeaturesApiClient() {
    this.httpClient = HttpClient.newBuilder().connectTimeout(CONNECTION_TIMEOUT).build();
    this.objectMapper =
        new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .findAndRegisterModules();
  }

  public void setFeatureFlag(Feature feature, boolean enabled, String token) {
    var url = API_URL + "/" + feature + "?enabled=" + enabled;
    var request =
        HttpRequest.newBuilder()
            .timeout(READ_TIMEOUT)
            .uri(URI.create(url))
            .header("Accept", "application/json")
            .header("Authorization", "Bearer " + token)
            .PUT(HttpRequest.BodyPublishers.noBody())
            .build();

    try {
      log.info("Setting feature toggle: " + feature + " to " + enabled);
      var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new FeaturesApiException(
            "Got incorrect status code when sending PUT to "
                + url
                + " - Status code: "
                + response.statusCode()
                + ", Response: "
                + response.body());
      }
    } catch (Exception e) {
      log.error("Failed to set feature toggle: " + feature, e);
    }
  }

  public List<FeatureFlagDto> getFeatureFlags(String token) {
    var url = API_URL;
    var request =
        HttpRequest.newBuilder()
            .timeout(READ_TIMEOUT)
            .uri(URI.create(url))
            .header("Accept", "application/json")
            .header("Authorization", "Bearer " + token)
            .GET()
            .build();

    try {
      var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new FeaturesApiException(
            "Got incorrect status code when sending GET to "
                + url
                + " - Status code: "
                + response.statusCode()
                + ", Response: "
                + response.body());
      }
      return objectMapper.readValue(
          response.body(),
          objectMapper.getTypeFactory().constructCollectionType(List.class, FeatureFlagDto.class));
    } catch (Exception e) {
      log.error("Failed to fetch feature flags", e);
      return List.of();
    }
  }
}
