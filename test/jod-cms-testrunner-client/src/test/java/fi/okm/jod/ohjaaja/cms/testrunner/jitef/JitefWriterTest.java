/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture,
 * The Finnish Ministry of Economic Affairs and Employment,
 * The Finnish National Agency of Education (Opetushallitus) and
 * The Finnish Development and Administration centre for ELY Centres
 * and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.testrunner.jitef;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import org.junit.Test;

public class JitefWriterTest {

  @Test
  public void writesRunRequestPayload() {
    String payload = JitefWriter.runRequest(42L, "fi.okm.ExampleTest", Arrays.asList("a", "b"));

    assertEquals(
        "{\"bundleId\":42,\"className\":\"fi.okm.ExampleTest\",\"filteredMethods\":[\"a\",\"b\"]}",
        payload);
  }

  @Test
  public void writesFailureEventPayloadAndEscapesStrings() {
    String payload =
        JitefWriter.testFailureEvent(
            "failure", "fi.okm.ExampleTest", "testX", "java.lang.RuntimeException", "a\"b\\c\n", "stack");

    assertEquals(
        "{\"type\":\"failure\",\"class\":\"fi.okm.ExampleTest\",\"method\":\"testX\",\"throwableClass\":\"java.lang.RuntimeException\",\"message\":\"a\\\"b\\\\c\\n\",\"stack\":\"stack\"}",
        payload);
  }

  @Test
  public void escapesAllSupportedControlCharacters() {
    String payload = JitefWriter.errorResponse("a\rb\tc\bd\fe\u0001", "ok");

    assertEquals("{\"error\":\"a\\rb\\tc\\bd\\fe\\u0001\",\"detail\":\"ok\"}", payload);
  }

  @Test
  public void writesEmptyArrayForNullFilteredMethods() {
    String payload = JitefWriter.runRequest(12L, "fi.okm.ExampleTest", null);

    assertEquals("{\"bundleId\":12,\"className\":\"fi.okm.ExampleTest\",\"filteredMethods\":[]}", payload);
  }

  @Test
  public void writesSimpleTestEventPayload() {
    String payload = JitefWriter.testEvent("started", "fi.okm.ExampleTest", "testX");

    assertEquals("{\"type\":\"started\",\"class\":\"fi.okm.ExampleTest\",\"method\":\"testX\"}", payload);
  }

  @Test
  public void writesServletSupportPayloads() {
    assertEquals("{\"bundleId\":99}", JitefWriter.installBundleResponse(99L));
    assertEquals("{\"status\":\"ok\"}", JitefWriter.statusOkResponse());
    assertEquals(
        "{\"type\":\"runError\",\"message\":\"boom\",\"throwableClass\":\"java.lang.IllegalStateException\"}",
        JitefWriter.runErrorEvent("boom", "java.lang.IllegalStateException"));
    assertEquals(
        "{\"error\":\"install failed\",\"detail\":\"bad\\nnews\"}",
        JitefWriter.errorResponse("install failed", "bad\nnews"));
  }

  @Test
  public void normalisesNullStringFieldsToEmptyStringsForRunError() {
    String payload = JitefWriter.runErrorEvent(null, null);

    assertEquals("{\"type\":\"runError\",\"message\":\"\",\"throwableClass\":\"\"}", payload);
  }

  @Test
  public void normalisesNullStringFieldsToEmptyStringsForFailureEvent() {
    String payload = JitefWriter.testFailureEvent("failure", null, null, null, null, null);

    assertEquals(
        "{\"type\":\"failure\",\"class\":\"\",\"method\":\"\",\"throwableClass\":\"\","
            + "\"message\":\"\",\"stack\":\"\"}",
        payload);
  }

  @Test
  public void roundtripWriterToReaderPreservesValues() {
    String payload =
        JitefWriter.runRequest(
            12345L, "fi.okm.Test\\Inner\"Case\"", Arrays.asList("m1", "line\nline", "tab\tmethod"));

    assertEquals("fi.okm.Test\\Inner\"Case\"", JitefReader.readRunRequestClassName(payload));
    assertEquals(12345L, JitefReader.readRunRequestBundleId(payload));
    assertEquals(
        Arrays.asList("m1", "line\nline", "tab\tmethod"),
        JitefReader.readRunRequestFilteredMethods(payload));
  }
}

