/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture,
 * The Finnish Ministry of Economic Affairs and Employment,
 * The Finnish National Agency of Education (Opetushallitus) and
 * The Finnish Development and Administration centre for ELY Centres
 * and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.testrunner.client;

import fi.okm.jod.ohjaaja.cms.testrunner.jitef.JitefReader;
import fi.okm.jod.ohjaaja.cms.testrunner.jitef.JitefWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thin HTTP client for the OSGi-side {@code TestRunnerServlet}.
 *
 * <p>Uses {@link HttpURLConnection} so we keep the dependency surface small and stay compatible
 * with the JDK that the Gradle test JVM runs under without pulling in extra HTTP libraries.
 */
public final class HttpRunnerClient {

  private static final String CONTENT_TYPE_JITEF = "application/x-jitef; charset=utf-8";

  /** Default time the {@link #waitForReady} loop is allowed to wait for the servlet to come up. */
  private static final long DEFAULT_READINESS_TIMEOUT_MS = 60_000L;

  /** Pause between {@code /ping} attempts in {@link #waitForReady}. */
  private static final long READINESS_POLL_INTERVAL_MS = 1_000L;

  private static final Logger log = Logger.getLogger(HttpRunnerClient.class.getName());

  private final URL baseUrl;

  public HttpRunnerClient(String baseUrl) {
    try {
      String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
      this.baseUrl = URI.create(normalized).toURL();
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid base URL: " + baseUrl, e);
    }
  }

  /**
   * Polls {@code GET /ping} until it returns HTTP 200 or the timeout elapses. The Docker readiness
   * check only waits for the Liferay portal root, so the {@code /o/jod-testrunner} servlet may
   * still be activating when the first test class runs. Calling this before the first install
   * request keeps a transient deployment race from failing the whole test class.
   *
   * @throws IOException if the servlet does not respond OK within the timeout
   */
  public void waitForReady() throws IOException {
    waitForReady(DEFAULT_READINESS_TIMEOUT_MS);
  }

  /** Variant of {@link #waitForReady()} with a caller-supplied timeout (in milliseconds). */
  public void waitForReady(long timeoutMillis) throws IOException {
    long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMillis);
    int attempt = 0;
    IOException lastFailure = null;
    while (true) {
      attempt++;
      try {
        if (pingOnce()) {
          if (attempt > 1) {
            log.log(Level.INFO, "Test runner servlet became ready after {0} attempts", attempt);
          }
          return;
        }
      } catch (IOException e) {
        lastFailure = e;
      }
      if (System.currentTimeMillis() >= deadline) {
        throw new IOException(
            "Test runner servlet at " + baseUrl + " not ready within " + timeoutMillis + " ms",
            lastFailure);
      }
      try {
        Thread.sleep(READINESS_POLL_INTERVAL_MS);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        throw new IOException("Interrupted while waiting for test runner servlet", ie);
      }
    }
  }

  private boolean pingOnce() throws IOException {
    HttpURLConnection conn = open("/ping", "GET");
    conn.setReadTimeout(5_000);
    try {
      return conn.getResponseCode() == HttpURLConnection.HTTP_OK;
    } finally {
      conn.disconnect();
    }
  }

  /** Installs a bundle. Returns the {@code bundleId} assigned by Liferay. */
  public long installBundle(byte[] bundleBytes) throws IOException {
    HttpURLConnection conn = open("/bundles", "POST");
    conn.setDoOutput(true);
    conn.setFixedLengthStreamingMode(bundleBytes.length);
    conn.setRequestProperty("Content-Type", "application/java-archive");
    try (OutputStream out = conn.getOutputStream()) {
      out.write(bundleBytes);
    }
    String body = readBody(conn);
    if (conn.getResponseCode() / 100 != 2) {
      throw new IOException(
          "Bundle install failed: HTTP " + conn.getResponseCode() + " - " + body);
    }
    return parseBundleId(body);
  }

  /** Uninstalls the bundle. */
  public void uninstallBundle(long bundleId) throws IOException {
    HttpURLConnection conn = open("/bundles/" + bundleId, "DELETE");
    int code = conn.getResponseCode();
    String body = readBody(conn);
    if (code / 100 != 2) {
      throw new IOException(
          "Bundle uninstall failed: HTTP " + code + " - " + body);
    }
  }

  /**
   * Starts a test run. Returns the chunked response stream so the caller can read NDJITEF events
   * incrementally. Caller is responsible for closing the stream.
   */
  public InputStream startRun(long bundleId, String className, List<String> excludedMethods)
      throws IOException {
    HttpURLConnection conn = open("/run", "POST");
    conn.setDoOutput(true);
    conn.setRequestProperty("Content-Type", CONTENT_TYPE_JITEF);
    String payload = buildRunPayload(bundleId, className, excludedMethods);
    byte[] body = payload.getBytes(StandardCharsets.UTF_8);
    conn.setFixedLengthStreamingMode(body.length);
    try (OutputStream out = conn.getOutputStream()) {
      out.write(body);
    }
    int code = conn.getResponseCode();
    if (code / 100 != 2) {
      String errorBody = readBody(conn);
      throw new IOException("Run start failed: HTTP " + code + " - " + errorBody);
    }
    return conn.getInputStream();
  }

  private HttpURLConnection open(String path, String method) throws IOException {
    URL url = URI.create(baseUrl.toString() + path).toURL();
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod(method);
    conn.setConnectTimeout(30_000);
    conn.setReadTimeout(0); // streaming endpoint: no timeout between bytes
    conn.setUseCaches(false);
    return conn;
  }

  private static String readBody(HttpURLConnection conn) {
    try (InputStream in = conn.getResponseCode() / 100 == 2 ? conn.getInputStream() : conn.getErrorStream()) {
      if (in == null) {
        return "";
      }
      ByteArrayOutputStream buf = new ByteArrayOutputStream();
      byte[] chunk = new byte[1024];
      int n;
      while ((n = in.read(chunk)) != -1) {
        buf.write(chunk, 0, n);
      }
      return buf.toString(StandardCharsets.UTF_8);
    } catch (IOException e) {
      return "";
    }
  }

  private static long parseBundleId(String body) throws IOException {
    if (!body.contains("\"bundleId\"")) {
      throw new IOException("Response missing bundleId: " + body);
    }
    return JitefReader.readInstallResponseBundleId(body);
  }

  private static String buildRunPayload(long bundleId, String className, List<String> excludedMethods) {
    return JitefWriter.runRequest(bundleId, className, excludedMethods);
  }
}
