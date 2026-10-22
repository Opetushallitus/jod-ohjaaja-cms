/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.studyprogram.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

/** Downloads study program images, accepting only successful responses that are an image. */
@Component(service = StudyProgramImageClient.class)
public class StudyProgramImageClient {

  /** Larger responses are rejected, so that a wrong URL cannot fill the memory. */
  static final int MAX_IMAGE_SIZE = 10 * 1024 * 1024;

  private static final String ERROR_PREFIX = "Failed to download image: ";

  /**
   * The file name extensions of the image types that are accepted, which browsers can show. Other
   * types, such as SVG, can contain scripts.
   */
  private static final Map<String, String> EXTENSIONS_BY_CONTENT_TYPE =
      Map.of(
          "image/jpeg", "jpg",
          "image/jpg", "jpg",
          "image/png", "png",
          "image/gif", "gif",
          "image/webp", "webp",
          "image/avif", "avif");

  private final HttpClient httpClient;
  private final Duration responseTimeout;

  public StudyProgramImageClient() {
    this(KonfoHttp.RESPONSE_TIMEOUT);
  }

  /**
   * @param responseTimeout how long receiving a whole response, including its body, may take
   */
  StudyProgramImageClient(Duration responseTimeout) {
    this.responseTimeout = responseTimeout;
    this.httpClient =
        HttpClient.newBuilder()
            .connectTimeout(KonfoHttp.CONNECTION_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  @Deactivate
  protected void deactivate() {
    httpClient.shutdownNow();
  }

  /**
   * @return the image
   * @throws IOException if the request fails, or the response is not a successful one with one of
   *     the accepted image types, such as an error page, or it is empty or larger than {@link
   *     #MAX_IMAGE_SIZE}
   */
  public Image downloadImage(String imageUrl) throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder()
            .timeout(KonfoHttp.READ_TIMEOUT)
            .uri(URI.create(imageUrl))
            .header("Accept", "image/*")
            .GET()
            .build();

    var response =
        KonfoHttp.send(
            httpClient,
            request,
            responseInfo ->
                new SizeLimitedBodySubscriber(
                    MAX_IMAGE_SIZE, ERROR_PREFIX + "larger than " + MAX_IMAGE_SIZE + " bytes"),
            responseTimeout);

    if (response.statusCode() != 200) {
      throw new IOException(ERROR_PREFIX + "HTTP status " + response.statusCode());
    }
    var contentType = getContentType(response.headers());
    if (contentType.isEmpty()) {
      throw new IOException(ERROR_PREFIX + "no content type");
    }
    var extension = EXTENSIONS_BY_CONTENT_TYPE.get(contentType);
    if (extension == null) {
      throw new IOException(ERROR_PREFIX + "content type '" + contentType + "'");
    }
    if (response.body().length == 0) {
      throw new IOException(ERROR_PREFIX + "empty response");
    }
    return new Image(response.body(), extension);
  }

  /**
   * @return the media type of the response without its parameters, or an empty string if it has
   *     none
   */
  private static String getContentType(HttpHeaders headers) {
    return headers
        .firstValue("Content-Type")
        .map(value -> value.split(";", 2)[0].strip().toLowerCase(Locale.ROOT))
        .orElse("");
  }

  /**
   * @param extension the file name extension of the image type
   */
  public record Image(byte[] bytes, String extension) {

    @Override
    public boolean equals(Object other) {
      return other instanceof Image(byte[] otherBytes, String otherExtension)
          && Arrays.equals(bytes, otherBytes)
          && Objects.equals(extension, otherExtension);
    }

    @Override
    public int hashCode() {
      return 31 * Arrays.hashCode(bytes) + Objects.hashCode(extension);
    }

    /** Leaves out the bytes, since an image can be large. */
    @Override
    public String toString() {
      return "Image[" + bytes.length + " bytes, extension=" + extension + "]";
    }
  }
}
