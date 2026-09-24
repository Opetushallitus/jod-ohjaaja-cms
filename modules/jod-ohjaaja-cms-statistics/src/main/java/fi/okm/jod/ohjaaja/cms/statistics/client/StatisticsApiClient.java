/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.statistics.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.PropsUtil;
import fi.okm.jod.ohjaaja.cms.statistics.client.exception.StatisticsApiException;
import fi.okm.jod.ohjaaja.cms.statistics.dto.StatisticsDto;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import org.osgi.service.component.annotations.Component;

@Component(service = StatisticsApiClient.class)
public class StatisticsApiClient {
  private static final Log log = LogFactoryUtil.getLog(StatisticsApiClient.class);
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

  private static final String API_URL =
      PropsUtil.get("ohjaaja.backend.url") + PropsUtil.get("ohjaaja.backend.statistics.api.path");

  public StatisticsApiClient() {
    this.httpClient = HttpClient.newBuilder().connectTimeout(CONNECTION_TIMEOUT).build();
    this.objectMapper =
        new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(new JavaTimeModule())
            .findAndRegisterModules();
  }

  public StatisticsDto fetchStatistics(String token, LocalDate alku, LocalDate loppu, int top)
      throws StatisticsApiException {
    var url = new StringBuilder(API_URL).append("?top=").append(top);
    if (alku != null) {
      url.append("&alku=").append(alku);
    }
    if (loppu != null) {
      url.append("&loppu=").append(loppu);
    }
    var request =
        HttpRequest.newBuilder()
            .timeout(READ_TIMEOUT)
            .uri(URI.create(url.toString()))
            .header("Accept", "application/json")
            .header("Authorization", "Bearer " + token)
            .GET()
            .build();

    HttpResponse<String> response;
    try {
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Interrupted while fetching statistics from " + url, e);
      throw new StatisticsApiException(e);
    } catch (Exception e) {
      log.error("Error while fetching statistics from " + url, e);
      throw new StatisticsApiException(e);
    }

    if (response.statusCode() != 200) {
      var message =
          "Failed to fetch statistics from url "
              + url
              + " - Status code: "
              + response.statusCode()
              + ", Response: "
              + response.body();
      log.error(message);
      throw new StatisticsApiException(message);
    }

    try {
      return objectMapper.readValue(response.body(), StatisticsDto.class);
    } catch (Exception e) {
      log.error("Error while parsing statistics response from " + url, e);
      throw new StatisticsApiException(e);
    }
  }
}
