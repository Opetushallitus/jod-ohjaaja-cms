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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

public class TestEventReaderTest {

  @Test
  public void parsesStartedEventWithRequiredFields() {
    TestEvent event = TestEventReader.parse("{\"type\":\"started\",\"class\":\"A\",\"method\":\"m\"}");

    assertEquals(TestEvent.Type.STARTED, event.type());
    assertEquals("A", event.className());
    assertEquals("m", event.methodName());
    assertNull(event.throwableClass());
    assertNull(event.message());
    assertNull(event.stack());
  }

  @Test
  public void parsesRunErrorWithoutClassAndMethod() {
    TestEvent event =
        TestEventReader.parse(
            "{\"type\":\"runError\",\"message\":\"boom\",\"throwableClass\":\"java.lang.IllegalStateException\"}");

    assertEquals(TestEvent.Type.RUN_ERROR, event.type());
    assertNull(event.className());
    assertNull(event.methodName());
    assertEquals("java.lang.IllegalStateException", event.throwableClass());
    assertEquals("boom", event.message());
    assertNull(event.stack());
  }

  @Test
  public void parsesFailureAssumptionFailureAndIgnoredEvents() {
    TestEvent failure =
        TestEventReader.parse(
            "{\"type\":\"failure\",\"class\":\"A\",\"method\":\"m\",\"throwableClass\":\"E\",\"message\":\"msg\",\"stack\":\"st\"}");
    assertEquals(TestEvent.Type.FAILURE, failure.type());

    TestEvent assumption =
        TestEventReader.parse(
            "{\"type\":\"assumptionFailure\",\"class\":\"A\",\"method\":\"m\",\"throwableClass\":\"E\",\"message\":\"msg\",\"stack\":\"st\"}");
    assertEquals(TestEvent.Type.ASSUMPTION_FAILURE, assumption.type());

    TestEvent ignored = TestEventReader.parse("{\"type\":\"ignored\",\"class\":\"A\",\"method\":\"m\"}");
    assertEquals(TestEvent.Type.IGNORED, ignored.type());
  }

  @Test
  public void parseThrowsWhenRequiredFieldsAreMissing() {
    assertThrows(
        IllegalArgumentException.class,
        () -> TestEventReader.parse("{\"type\":\"started\",\"class\":\"A\"}"));
    assertThrows(
        IllegalArgumentException.class,
        () -> TestEventReader.parse("{\"type\":\"runError\",\"throwableClass\":\"X\"}"));
    assertThrows(
        IllegalArgumentException.class,
        () -> TestEventReader.parse("{\"type\":\"unknown\",\"class\":\"A\",\"method\":\"m\"}"));
  }

  @Test
  public void nextWrapsInvalidPayloadAsIOException() throws Exception {
    byte[] payload = "{\"type\":\"started\",\"class\":\"A\"}\n".getBytes(StandardCharsets.UTF_8);
    try (TestEventReader reader = new TestEventReader(new ByteArrayInputStream(payload))) {
      assertThrows(IOException.class, reader::next);
    }
  }

  @Test
  public void nextSkipsBlankLinesAndReturnsNullAtEof() throws Exception {
    byte[] payload = "\n \n{\"type\":\"started\",\"class\":\"A\",\"method\":\"m\"}\n".getBytes(StandardCharsets.UTF_8);
    try (TestEventReader reader = new TestEventReader(new ByteArrayInputStream(payload))) {
      TestEvent event = reader.next();
      Assert.assertNotNull(event);
      assertEquals(TestEvent.Type.STARTED, event.type());
      assertNull(reader.next());
    }
  }
}

