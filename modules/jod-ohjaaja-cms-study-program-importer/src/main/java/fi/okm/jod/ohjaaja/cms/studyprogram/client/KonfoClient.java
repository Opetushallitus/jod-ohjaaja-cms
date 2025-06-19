/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.studyprogram.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.okm.jod.ohjaaja.cms.studyprogram.dto.StudyProgramDto;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.osgi.service.component.annotations.Component;

@Component(service = KonfoClient.class)
public class KonfoClient {

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

  public KonfoClient() {
    this.httpClient = HttpClient.newBuilder().connectTimeout(CONNECTION_TIMEOUT).build();
    this.objectMapper =
        new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .findAndRegisterModules();
  }

  public List<StudyProgramDto> fetchStudyPrograms() throws IOException, InterruptedException {
    // TODO: Change the URL to correct one when correct Konfo API endpoint is available
    String url =
        "https://opintopolku.fi/konfo-backend/external/search/toteutukset-koulutuksittain"
            + "?keyword=ohjaaja"
            + "&hakukaynnissa=false"
            + "&jotpa=false"
            + "&tyovoimakoulutus=false"
            + "&taydennyskoulutus=false"
            + "&size=1000";

    HttpRequest request =
        HttpRequest.newBuilder()
            .timeout(READ_TIMEOUT)
            .uri(URI.create(url))
            .header("Accept", "application/json")
            .GET()
            .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() == 200) {
      KonfoResponse searchResponse = objectMapper.readValue(response.body(), KonfoResponse.class);
      // TODO: Remove the limit of 10 results when the fetching of study programs is stable
      return searchResponse.hits().subList(0, 10);
    } else {
      throw new IOException("Failed to fetch study programs: " + response.statusCode());
    }
  }
}
