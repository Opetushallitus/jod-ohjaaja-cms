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
import java.net.URISyntaxException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for downloading study program images. */
public class StudyProgramImageClientTest {

  private static final byte[] IMAGE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
  private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};

  private final StudyProgramImageClient client = new StudyProgramImageClient();
  private final CountDownLatch stalledBodiesReleased = new CountDownLatch(1);
  private HttpServer server;

  @Before
  public void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    respond("/image.png", 200, "image/png", IMAGE);
    respond("/photo", 200, "image/jpeg", JPEG);
    respond("/redirect.png", 302, null, new byte[0], "/image.png");
    respond(
        "/missing.png", 404, "text/html", "<h1>Not found</h1>".getBytes(StandardCharsets.UTF_8));
    respond(
        "/page.png",
        200,
        "text/html; charset=utf-8",
        "<h1>Error</h1>".getBytes(StandardCharsets.UTF_8));
    respond(
        "/image.svg",
        200,
        "image/svg+xml",
        "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
            .getBytes(StandardCharsets.UTF_8));
    respond("/untyped.png", 200, null, IMAGE);
    respond("/binary.png", 200, "binary/octet-stream", IMAGE);
    respond("/empty.png", 200, "image/png", new byte[0]);
    respond("/large.png", 200, "image/png", new byte[StudyProgramImageClient.MAX_IMAGE_SIZE + 1]);
    server.createContext(
        "/large-unknown-length.png",
        exchange -> {
          exchange.getResponseHeaders().set("Content-Type", "image/png");
          // A length of 0 sends the body in chunks, without a Content-Length header
          exchange.sendResponseHeaders(200, 0);
          try (var outputStream = exchange.getResponseBody()) {
            outputStream.write(new byte[StudyProgramImageClient.MAX_IMAGE_SIZE + 1]);
          } catch (IOException e) {
            // The client stops reading once the body is too large
          }
        });
    respondWithStalledBody("/stalled.png", 200, "image/png");
    server.start();
  }

  @After
  public void tearDown() {
    client.deactivate();
    // The server waits for its handlers to return before it stops
    stalledBodiesReleased.countDown();
    server.stop(0);
  }

  @Test
  public void testDownloadsImage() throws Exception {
    var image = client.downloadImage(url("/image.png"));
    Assert.assertArrayEquals(IMAGE, image.bytes());
    Assert.assertEquals("png", image.extension());
  }

  @Test
  public void testTellsExtensionByContentType() throws Exception {
    var image = client.downloadImage(url("/photo"));
    Assert.assertArrayEquals(JPEG, image.bytes());
    Assert.assertEquals("jpg", image.extension());
  }

  @Test
  public void testDoesNotFollowRedirect() {
    var e =
        Assert.assertThrows(IOException.class, () -> client.downloadImage(url("/redirect.png")));
    Assert.assertEquals("Failed to download image: HTTP status 302", e.getMessage());
  }

  @Test
  public void testRejectsErrorStatus() {
    var e = Assert.assertThrows(IOException.class, () -> client.downloadImage(url("/missing.png")));
    Assert.assertEquals("Failed to download image: HTTP status 404", e.getMessage());
  }

  @Test
  public void testRejectsImageWithoutImageContentType() {
    var untyped =
        Assert.assertThrows(IOException.class, () -> client.downloadImage(url("/untyped.png")));
    Assert.assertEquals("Failed to download image: no content type", untyped.getMessage());
    var binary =
        Assert.assertThrows(IOException.class, () -> client.downloadImage(url("/binary.png")));
    Assert.assertEquals(
        "Failed to download image: content type 'binary/octet-stream'", binary.getMessage());
  }

  @Test
  public void testRejectsResponseThatIsNotAnImage() {
    Assert.assertThrows(IOException.class, () -> client.downloadImage(url("/page.png")));
  }

  @Test
  public void testRejectsSvgImage() {
    var e = Assert.assertThrows(IOException.class, () -> client.downloadImage(url("/image.svg")));
    Assert.assertEquals("Failed to download image: content type 'image/svg+xml'", e.getMessage());
  }

  @Test
  public void testRejectsEmptyImage() {
    Assert.assertThrows(IOException.class, () -> client.downloadImage(url("/empty.png")));
  }

  @Test
  public void testRejectsTooLargeImage() {
    var e = Assert.assertThrows(IOException.class, () -> client.downloadImage(url("/large.png")));
    Assert.assertTrue(e.getMessage(), e.getMessage().contains("larger than"));
  }

  @Test
  public void testRejectsTooLargeImageOfUnknownLength() {
    var e =
        Assert.assertThrows(
            IOException.class, () -> client.downloadImage(url("/large-unknown-length.png")));
    Assert.assertTrue(e.getMessage(), e.getMessage().contains("larger than"));
  }

  @Test(timeout = 10_000)
  public void testTimesOutWhenBodyStalls() {
    var timingOutClient = new StudyProgramImageClient(Duration.ofMillis(500));
    try {
      Assert.assertThrows(
          HttpTimeoutException.class, () -> timingOutClient.downloadImage(url("/stalled.png")));
    } finally {
      timingOutClient.deactivate();
    }
  }

  /** Uses the address the server is bound to, which "localhost" might not resolve to. */
  private String url(String path) throws URISyntaxException {
    var address = server.getAddress();
    return new URI("http", null, address.getHostString(), address.getPort(), path, null, null)
        .toString();
  }

  /** The headers and the start of the body arrive, but the rest of the body does not. */
  private void respondWithStalledBody(String path, int status, String contentType) {
    server.createContext(
        path,
        exchange -> {
          exchange.getResponseHeaders().set("Content-Type", contentType);
          exchange.sendResponseHeaders(status, IMAGE.length);
          try (var outputStream = exchange.getResponseBody()) {
            outputStream.write(IMAGE, 0, 2);
            outputStream.flush();
            stalledBodiesReleased.await(10, TimeUnit.SECONDS);
          } catch (IOException e) {
            // The client has closed the connection
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        });
  }

  private void respond(String path, int status, String contentType, byte[] body) {
    respond(path, status, contentType, body, null);
  }

  private void respond(String path, int status, String contentType, byte[] body, String location) {
    server.createContext(
        path,
        exchange -> {
          if (contentType != null) {
            exchange.getResponseHeaders().set("Content-Type", contentType);
          }
          if (location != null) {
            exchange.getResponseHeaders().set("Location", location);
          }
          exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
          try (var outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
          }
        });
  }
}
