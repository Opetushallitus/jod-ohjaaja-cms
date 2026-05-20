/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture,
 * The Finnish Ministry of Economic Affairs and Employment,
 * The Finnish National Agency of Education (Opetushallitus) and
 * The Finnish Development and Administration centre for ELY Centres
 * and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.testrunner.runtime;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import fi.okm.jod.ohjaaja.cms.testrunner.jitef.JitefWriter;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

/**
 * JUnit {@link RunListener} that serialises test events into newline-delimited JITEF (NDJITEF) and writes them
 * to an {@link OutputStream}, flushing after every line so the host receives events as they happen.
 *
 * <p>Event shapes (one JITEF object per line):
 *
 * <pre>
 *   {"type":"started","class":"...","method":"..."}
 *   {"type":"finished","class":"...","method":"..."}
 *   {"type":"failure","class":"...","method":"...","throwableClass":"...","message":"...","stack":"..."}
 *   {"type":"assumptionFailure","class":"...","method":"...","throwableClass":"...","message":"...","stack":"..."}
 *   {"type":"ignored","class":"...","method":"..."}
 * </pre>
 *
 * <p>Stack traces are serialised as plain text rather than as a {@code Throwable} to avoid Java
 * serialisation altogether. End-of-run is signalled by closing the underlying stream, so no
 * explicit summary event is emitted.
 */
public final class NdJitefRunListener extends RunListener {

  private final OutputStream out;

  public NdJitefRunListener(OutputStream out) {
    this.out = out;
  }

  @Override
  public void testStarted(Description description) throws Exception {
    writeDescriptionLine("started", description);
  }

  @Override
  public void testFinished(Description description) throws Exception {
    writeDescriptionLine("finished", description);
  }

  @Override
  public void testFailure(Failure failure) throws Exception {
    writeFailureLine("failure", failure);
  }

  @Override
  public void testAssumptionFailure(Failure failure) {
    try {
      writeFailureLine("assumptionFailure", failure);
    } catch (IOException e) {
      // testAssumptionFailure cannot declare checked exceptions; surface I/O failures as a more
      // specific unchecked type so callers can recognise them.
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public void testIgnored(Description description) throws Exception {
    writeDescriptionLine("ignored", description);
  }

  private void writeDescriptionLine(String type, Description description) throws IOException {
    writeLine(JitefWriter.testEvent(type, description.getClassName(), description.getMethodName()));
  }

  private void writeFailureLine(String type, Failure failure) throws IOException {
    Description description = failure.getDescription();
    Throwable throwable = failure.getException();
    String throwableClass = throwable == null ? "" : throwable.getClass().getName();
    String message = throwable == null ? "" : String.valueOf(throwable.getMessage());
    String stack = "";
    if (throwable != null) {
      StringWriter sw = new StringWriter();
      throwable.printStackTrace(new PrintWriter(sw));
      stack = sw.toString();
    }
    // Class-level failures (e.g. failures raised by @BeforeClass, class @Rules, or
    // reportLoadFailure) carry a suite Description whose method name is null. NDJITEF FAILURE
    // events require both class and method to be strings, so we substitute JUnit's conventional
    // sentinel "initializationError" so the host can still attribute the failure to the test
    // class instead of rejecting the whole stream as malformed.
    String className = description.getClassName();
    String methodName = description.getMethodName();
    if (methodName == null) {
      methodName = "initializationError";
    }
    if (className == null) {
      className = "";
    }
    writeLine(
        JitefWriter.testFailureEvent(type, className, methodName, throwableClass, message, stack));
  }

  private void writeLine(String payload) throws IOException {
    out.write(payload.getBytes(StandardCharsets.UTF_8));
    out.write('\n');
    out.flush();
  }
}

