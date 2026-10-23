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
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Timeouts of the requests to Konfo, shared by its clients, and sending a request with a timeout
 * for the whole response.
 */
final class KonfoHttp {
  static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(30);
  static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

  /**
   * How long receiving a whole response may take. The timeout of a request ends when the response
   * headers have been received, so this also covers the body, which gets at least as long.
   */
  static final Duration RESPONSE_TIMEOUT = READ_TIMEOUT.multipliedBy(2);

  private KonfoHttp() {}

  /**
   * Sends the request like {@link HttpClient#send}, but also fails if the whole response, including
   * its body, is not received within the given time.
   *
   * @throws HttpTimeoutException if the response is not received in time, or the request times out
   */
  static <T> HttpResponse<T> send(
      HttpClient httpClient,
      HttpRequest request,
      HttpResponse.BodyHandler<T> bodyHandler,
      Duration responseTimeout)
      throws IOException, InterruptedException {
    var responseFuture = httpClient.sendAsync(request, bodyHandler);
    try {
      return responseFuture.get(responseTimeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      // Cancelling the request also closes its connection
      responseFuture.cancel(true);
      throw new HttpTimeoutException(
          "Response not received in " + responseTimeout.toMillis() + " ms");
    } catch (InterruptedException e) {
      responseFuture.cancel(true);
      throw e;
    } catch (ExecutionException e) {
      var cause = e.getCause();
      // Unchecked exceptions, such as errors of a body handler or running out of memory, are thrown
      // as they are, so that they are not taken for a failed request
      if (cause instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      // Wrapped like HttpClient.send() does, so that the stack trace also shows the caller and a
      // timeout is still a timeout
      IOException exception;
      if (cause instanceof HttpConnectTimeoutException) {
        exception = new HttpConnectTimeoutException(cause.getMessage());
      } else if (cause instanceof HttpTimeoutException) {
        exception = new HttpTimeoutException(cause.getMessage());
      } else {
        exception = new IOException(cause.getMessage());
      }
      exception.initCause(cause);
      throw exception;
    }
  }
}
