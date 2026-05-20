/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture,
 * The Finnish Ministry of Economic Affairs and Employment,
 * The Finnish National Agency of Education (Opetushallitus) and
 * The Finnish Development and Administration centre for ELY Centres
 * and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.testrunner.osgi.servlet;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import fi.okm.jod.ohjaaja.cms.testrunner.jitef.JitefReader;
import fi.okm.jod.ohjaaja.cms.testrunner.jitef.JitefWriter;
import fi.okm.jod.ohjaaja.cms.testrunner.osgi.dispatch.TestExecutorDispatcher;
import fi.okm.jod.ohjaaja.cms.testrunner.osgi.store.TestBundleStore;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

/**
 * HTTP endpoint that lets a host-side test runner install OSGi bundles and dispatch JUnit runs.
 *
 * <p>Endpoints (all relative to the servlet pattern, served by Liferay's HTTP whiteboard):
 *
 * <ul>
 *   <li>{@code POST /bundles} - request body is a raw OSGi bundle JAR; installs and starts it.
 *       Responds with {@code 200} and body {@code {"bundleId": N}}.
 *   <li>{@code DELETE /bundles/{bundleId}} - uninstalls the previously installed bundle.
 *   <li>{@code POST /run} - request body is JITEF {@code {"bundleId": N, "className": "...",
 *       "filteredMethods": ["m1", "m2"]}}; streams test events back as NDJITEF
 *       using chunked transfer encoding.
 * </ul>
 *
 * <p>The endpoint is intentionally unauthenticated; it must only be exposed inside the disposable
 * Docker test container.
 */
@Component(
    service = Servlet.class,
    property = {
        "osgi.http.whiteboard.servlet.pattern=" + TestRunnerServlet.URL_PATTERN,
        "osgi.http.whiteboard.servlet.name=jod-testrunner",
        "osgi.http.whiteboard.context.select=(osgi.http.whiteboard.context.name=default)"
    })
public class TestRunnerServlet extends HttpServlet {

  public static final String URL_PATTERN = "/jod-testrunner/*";

  @Serial
  private static final long serialVersionUID = 1L;

  private static final Log log = LogFactoryUtil.getLog(TestRunnerServlet.class);

  private static final String CONTENT_TYPE_JITEF = "application/x-jitef; charset=utf-8";
  private static final String CONTENT_TYPE_NDJITEF = "application/x-ndjitef; charset=utf-8";

  private final transient TestBundleStore bundleStore;

  @Activate
  public TestRunnerServlet(BundleContext bundleContext) {
    this.bundleStore = new TestBundleStore(bundleContext);
    log.info("TestRunnerServlet activated on " + URL_PATTERN);
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
    String path = pathInfoOrEmpty(req);
    try {
      if ("/bundles".equals(path)) {
        handleInstallBundle(req, resp);
      } else if ("/run".equals(path)) {
        handleRun(req, resp);
      } else {
        resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Unknown POST path: " + path);
      }
    } catch (IOException e) {
      logIoFailure("POST " + path, e);
    }
  }

  @Override
  protected void doDelete(HttpServletRequest req, HttpServletResponse resp) {
    String path = pathInfoOrEmpty(req);
    try {
      if (path.startsWith("/bundles/")) {
        String idStr = path.substring("/bundles/".length());
        handleUninstallBundle(idStr, resp);
      } else {
        resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Unknown DELETE path: " + path);
      }
    } catch (IOException e) {
      logIoFailure("DELETE " + path, e);
    }
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
    String path = pathInfoOrEmpty(req);
    try {
      if ("/ping".equals(path) || "/".equals(path) || path.isEmpty()) {
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType(CONTENT_TYPE_JITEF);
        resp.getWriter().write(JitefWriter.statusOkResponse());
      } else {
        resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Unknown GET path: " + path);
      }
    } catch (IOException e) {
      logIoFailure("GET " + path, e);
    }
  }

  private static void logIoFailure(String context, IOException e) {
    // The response stream may already be closed at this point; we cannot reliably write back to the
    // client, so just log so the failure shows up in the container log.
    log.error("I/O failure handling " + context, e);
  }

  private void handleInstallBundle(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
    byte[] bundleBytes = readAllBytes(req.getInputStream());
    try {
      long bundleId = bundleStore.install(bundleBytes);
      resp.setStatus(HttpServletResponse.SC_OK);
      resp.setContentType(CONTENT_TYPE_JITEF);
      resp.getWriter().write(JitefWriter.installBundleResponse(bundleId));
    } catch (Exception e) {
      sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "install failed", e);
    }
  }

  private void handleUninstallBundle(String idStr, HttpServletResponse resp) throws IOException {
    long bundleId;
    try {
      bundleId = Long.parseLong(idStr);
    } catch (NumberFormatException e) {
      resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid bundle id: " + idStr);
      return;
    }
    try {
      bundleStore.uninstall(bundleId);
      resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
    } catch (Exception e) {
      sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "uninstall failed", e);
    }
  }

  private void handleRun(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String body = readAllAsString(req.getInputStream());
    RunRequest runRequest;
    try {
      runRequest = RunRequest.parse(body);
    } catch (RuntimeException e) {
      resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid request: " + e.getMessage());
      return;
    }

    Bundle bundle;
    try {
      bundle = bundleStore.get(runRequest.bundleId);
    } catch (Exception e) {
      sendError(resp, HttpServletResponse.SC_NOT_FOUND, "bundle not found", e);
      return;
    }

    resp.setStatus(HttpServletResponse.SC_OK);
    resp.setContentType(CONTENT_TYPE_NDJITEF);
    // Make sure response is flushed in chunks (chunked transfer encoding kicks in automatically
    // when no Content-Length is set and we flush early).
    resp.setBufferSize(256);
    OutputStream out = resp.getOutputStream();
    try {
      TestExecutorDispatcher.dispatch(
          bundle, runRequest.className, runRequest.filteredMethods, out);
      out.flush();
    } catch (Exception t) {
      // Best-effort: write an error event and rethrow as IOException so the servlet logs it.
      try {
        String payload = JitefWriter.runErrorEvent(String.valueOf(t.getMessage()), t.getClass().getName()) + "\n";
        out.write(payload.getBytes(StandardCharsets.UTF_8));
        out.flush();
      } catch (IOException ignored) {
        // swallow secondary failures
      }
      throw new IOException("Test dispatch failed", t);
    }
  }

  private static String pathInfoOrEmpty(HttpServletRequest req) {
    String pathInfo = req.getPathInfo();
    return pathInfo == null ? "" : pathInfo;
  }

  private static byte[] readAllBytes(InputStream in) throws IOException {
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    byte[] chunk = new byte[8192];
    int n;
    while ((n = in.read(chunk)) != -1) {
      buf.write(chunk, 0, n);
    }
    return buf.toByteArray();
  }

  private static String readAllAsString(InputStream in) throws IOException {
    StringBuilder sb = new StringBuilder();
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      char[] buf = new char[4096];
      int n;
      while ((n = reader.read(buf)) != -1) {
        sb.append(buf, 0, n);
      }
    }
    return sb.toString();
  }

  private static void sendError(HttpServletResponse resp, int status, String message, Exception e)
      throws IOException {
    log.error(message, e);
    resp.setStatus(status);
    resp.setContentType(CONTENT_TYPE_JITEF);
    resp.getWriter().write(JitefWriter.errorResponse(message, String.valueOf(e.getMessage())));
  }

  /** Decoded request body for {@code POST /run}. */
  static final class RunRequest {
    final long bundleId;
    final String className;
    final List<String> filteredMethods;

    private RunRequest(long bundleId, String className, List<String> filteredMethods) {
      this.bundleId = bundleId;
      this.className = className;
      this.filteredMethods = filteredMethods;
    }

    /**
     * Parses a flat JITEF object with three fields. The
     * client is under our control and produces a stable, simple shape.
     */
    static RunRequest parse(String payload) {
      String className = JitefReader.readRunRequestClassName(payload);
      if (className.isEmpty()) {
        throw new IllegalArgumentException("Missing 'className'");
      }
      long bundleId = JitefReader.readRunRequestBundleId(payload);
      List<String> filteredMethods = JitefReader.readRunRequestFilteredMethods(payload);
      return new RunRequest(bundleId, className, filteredMethods);
    }
  }
}
