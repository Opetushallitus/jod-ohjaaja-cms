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
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.PropsUtil;
import fi.okm.jod.ohjaaja.cms.comments.moderation.client.exception.ModerationApiException;
import fi.okm.jod.ohjaaja.cms.comments.moderation.dto.CommentDto;
import fi.okm.jod.ohjaaja.cms.comments.moderation.dto.CommentReportSummaryDto;
import fi.okm.jod.ohjaaja.cms.comments.moderation.dto.PageDto;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.osgi.service.component.annotations.Component;

@Component(service = CommentsModerationApiClient.class)
public class CommentsModerationApiClient {
  private static final Log log = LogFactoryUtil.getLog(CommentsModerationApiClient.class);
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

  private static final String API_BASE_PATH = "/internal-api/moderointi/kommentit";
  private static final String API_URL = PropsUtil.get("ohjaaja.backend.url") + API_BASE_PATH;

  public CommentsModerationApiClient() {
    this.httpClient = HttpClient.newBuilder().connectTimeout(CONNECTION_TIMEOUT).build();
    this.objectMapper =
        new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(new JavaTimeModule())
            .findAndRegisterModules();
  }

  public List<CommentReportSummaryDto> fetchCommentReportSummaryList(String token)
      throws ModerationApiException {

    var url = API_URL + "/ilmiannot";
    var request = createRequestBuilder(url, token).GET().build();

    try {
      var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        return objectMapper
            .readerForListOf(CommentReportSummaryDto.class)
            .readValue(response.body());
      } else {
        throw new ModerationApiException(
            "Failed to fetch kommentti report summaries from url "
                + url
                + " - Status code: "
                + response.statusCode()
                + ", Response: "
                + response.body());
      }
    } catch (Exception e) {
      log.error("Error while fetching kommentti report summaries from " + url, e);
      throw new ModerationApiException(e);
    }
  }

  public PageDto<CommentDto> fetchComments(String token, int page, int pageSize)
      throws ModerationApiException {
    var url = API_URL + "?sivu=" + page + "&koko=" + pageSize;
    var request = createRequestBuilder(url, token).GET().build();

    try {
      var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        return objectMapper.readValue(
            response.body(),
            objectMapper.getTypeFactory().constructParametricType(PageDto.class, CommentDto.class));
      } else {
        throw new ModerationApiException(
            "Failed to fetch comments from url "
                + url
                + " - Status code: "
                + response.statusCode()
                + ", Response: "
                + response.body());
      }
    } catch (Exception e) {
      log.error("Error while fetching comments from " + url, e);
      throw new ModerationApiException(e);
    }
  }

  public void deleteCommentReports(UUID commentId, String token) throws ModerationApiException {
    var url = API_URL + "/ilmiannot/" + commentId;
    sendDeleteRequest(url, token);
  }

  public void deleteComment(UUID commentId, String token) throws ModerationApiException {
    var url = API_URL + "/" + commentId;
    sendDeleteRequest(url, token);
  }

  private HttpRequest.Builder createRequestBuilder(String url, String token) {
    return HttpRequest.newBuilder()
        .timeout(READ_TIMEOUT)
        .uri(URI.create(url))
        .header("Accept", "application/json")
        .header("Authorization", "Bearer " + token);
  }

  private void sendDeleteRequest(String url, String token) throws ModerationApiException {
    var request = createRequestBuilder(url, token).DELETE().build();
    try {
      var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new ModerationApiException(
            "Got incorrect status code when sending DELETE to "
                + url
                + " - Status code: "
                + response.statusCode()
                + ", Response: "
                + response.body());
      }
    } catch (Exception e) {
      log.error("Error while sending DELETE to " + url, e);
      throw new ModerationApiException(e);
    }
  }
}
