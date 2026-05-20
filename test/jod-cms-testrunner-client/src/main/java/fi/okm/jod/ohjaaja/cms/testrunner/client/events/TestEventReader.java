/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture,
 * The Finnish Ministry of Economic Affairs and Employment,
 * The Finnish National Agency of Education (Opetushallitus) and
 * The Finnish Development and Administration centre for ELY Centres
 * and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.testrunner.client.events;

import fi.okm.jod.ohjaaja.cms.testrunner.jitef.JitefReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Reads NDJITEF {@link TestEvent} objects from an input stream one line at a time. */
public final class TestEventReader implements AutoCloseable {

  private final BufferedReader reader;

  public TestEventReader(InputStream in) {
    this.reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
  }

  /** Returns the next event, or {@code null} when the stream is exhausted. */
  public TestEvent next() throws IOException {
    String line;
    while ((line = reader.readLine()) != null) {
      String trimmed = line.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      try {
        return parse(trimmed);
      } catch (IllegalArgumentException e) {
        throw new IOException("Invalid NDJITEF event payload: " + trimmed, e);
      }
    }
    return null;
  }

  @Override
  public void close() throws IOException {
    reader.close();
  }

  static TestEvent parse(String payload) {
    TestEvent.Type type = TestEvent.Type.from(JitefReader.readEventType(payload));
    return switch (type) {
      case STARTED, FINISHED, IGNORED ->
          new TestEvent(
              type,
              JitefReader.readEventClass(payload),
              JitefReader.readEventMethod(payload),
              null,
              null,
              null);
      case FAILURE, ASSUMPTION_FAILURE ->
          new TestEvent(
              type,
              JitefReader.readEventClass(payload),
              JitefReader.readEventMethod(payload),
              JitefReader.readEventThrowableClass(payload),
              JitefReader.readEventMessage(payload),
              JitefReader.readEventStack(payload));
      case RUN_ERROR ->
          new TestEvent(
              type,
              null,
              null,
              JitefReader.readEventThrowableClass(payload),
              JitefReader.readEventMessage(payload),
              null);
    };
  }
}
