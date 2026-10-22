/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.studyprogram.client;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for sending requests to Konfo with a timeout for the whole response. */
public class KonfoHttpTest {

  private static final Duration RESPONSE_TIMEOUT = Duration.ofMillis(500);

  private final HttpClient httpClient = HttpClient.newHttpClient();
  private final CountDownLatch stalledResponsesReleased = new CountDownLatch(1);
  private HttpServer server;

  @Before
  public void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/complete",
        exchange -> {
          var body = "{}".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          try (var outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
          }
        });
    server.createContext(
        "/silent",
        exchange -> {
          // Not even the headers arrive
          try {
            stalledResponsesReleased.await(10, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          exchange.close();
        });
    server.createContext(
        "/stalled",
        exchange -> {
          // A length of 0 sends the body in chunks, so that the client waits for the last one
          exchange.sendResponseHeaders(200, 0);
          try (var outputStream = exchange.getResponseBody()) {
            // The headers and the start of the body arrive, but the rest of it does not
            outputStream.write('{');
            outputStream.flush();
            stalledResponsesReleased.await(10, TimeUnit.SECONDS);
          } catch (IOException e) {
            // The client has closed the connection
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        });
    server.start();
  }

  @After
  public void tearDown() {
    httpClient.shutdownNow();
    // The server waits for its handler to return before it stops
    stalledResponsesReleased.countDown();
    server.stop(0);
  }

  @Test
  public void testReturnsCompleteResponse() throws Exception {
    var response =
        KonfoHttp.send(
            httpClient,
            request("/complete"),
            HttpResponse.BodyHandlers.ofString(),
            RESPONSE_TIMEOUT);
    Assert.assertEquals("{}", response.body());
  }

  @Test(timeout = 10_000)
  public void testTimesOutWhenBodyStalls() {
    Assert.assertThrows(
        HttpTimeoutException.class,
        () ->
            KonfoHttp.send(
                httpClient,
                request("/stalled"),
                HttpResponse.BodyHandlers.ofString(),
                RESPONSE_TIMEOUT));
  }

  @Test(timeout = 10_000)
  public void testRequestTimeoutIsStillTimeout() throws Exception {
    // The request times out long before the whole response would
    var request = request("/silent", Duration.ofMillis(100));
    Assert.assertThrows(
        HttpTimeoutException.class,
        () ->
            KonfoHttp.send(
                httpClient, request, HttpResponse.BodyHandlers.ofString(), Duration.ofSeconds(5)));
  }

  @Test
  public void testThrowsUncheckedExceptionAsItIs() {
    // Fails like a body handler with a bug would
    HttpResponse.BodyHandler<String> failingBodyHandler =
        responseInfo ->
            HttpResponse.BodySubscribers.mapping(
                HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8),
                body -> {
                  throw new IllegalStateException("Bug in body handler");
                });
    var e =
        Assert.assertThrows(
            IllegalStateException.class,
            () ->
                KonfoHttp.send(
                    httpClient, request("/complete"), failingBodyHandler, RESPONSE_TIMEOUT));
    Assert.assertEquals("Bug in body handler", e.getMessage());
  }

  private HttpRequest request(String path) throws Exception {
    return request(path, KonfoHttp.READ_TIMEOUT);
  }

  private HttpRequest request(String path, Duration timeout) throws Exception {
    var address = server.getAddress();
    var uri = new URI("http", null, address.getHostString(), address.getPort(), path, null, null);
    return HttpRequest.newBuilder(uri).timeout(timeout).GET().build();
  }
}
