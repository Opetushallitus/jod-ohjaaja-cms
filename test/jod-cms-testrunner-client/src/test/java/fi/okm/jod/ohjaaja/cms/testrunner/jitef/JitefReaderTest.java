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
import static org.junit.Assert.assertThrows;

import java.util.Arrays;
import org.junit.Test;

public class JitefReaderTest {

  @Test
  public void readsRunRequestFields() {
    String payload =
        "{\"bundleId\":123,\"className\":\"line1\\nline2 \\\"q\\\" \\\\ slash\\/ uni\\u0041\",\"filteredMethods\":[\"m1\",1,null,\"m2\"]}";

    assertEquals(123L, JitefReader.readRunRequestBundleId(payload));
    assertEquals("line1\nline2 \"q\" \\ slash/ uniA", JitefReader.readRunRequestClassName(payload));
    assertEquals(Arrays.asList("m1", "m2"), JitefReader.readRunRequestFilteredMethods(payload));
  }

  @Test
  public void readsEventFields() {
    String payload =
        "{\"type\":\"failure\",\"class\":\"fi.okm.Example\",\"method\":\"testX\",\"throwableClass\":\"java.lang.RuntimeException\",\"message\":\"boom\",\"stack\":\"stack\"}";

    assertEquals("failure", JitefReader.readEventType(payload));
    assertEquals("fi.okm.Example", JitefReader.readEventClass(payload));
    assertEquals("testX", JitefReader.readEventMethod(payload));
    assertEquals("java.lang.RuntimeException", JitefReader.readEventThrowableClass(payload));
    assertEquals("boom", JitefReader.readEventMessage(payload));
    assertEquals("stack", JitefReader.readEventStack(payload));
  }

  @Test
  public void installResponseBundleIdFallsBackToZeroWhenInvalid() {
    assertEquals(42L, JitefReader.readInstallResponseBundleId("{\"bundleId\":42}"));
    assertEquals(7L, JitefReader.readInstallResponseBundleId("{\"bundleId\":+7}"));
    assertEquals(-3L, JitefReader.readInstallResponseBundleId("{\"bundleId\":-3}"));
    assertThrows(IllegalArgumentException.class, () -> JitefReader.readInstallResponseBundleId("{}"));
    assertThrows(
        IllegalArgumentException.class,
        () -> JitefReader.readInstallResponseBundleId("{\"bundleId\":12x}"));
    assertThrows(
        IllegalArgumentException.class,
        () -> JitefReader.readInstallResponseBundleId("{\"bundleId\":+}"));
  }

  @Test
  public void runRequestFieldsThrowWhenMissingOrMalformed() {
    assertThrows(IllegalArgumentException.class, () -> JitefReader.readRunRequestClassName("{}"));
    assertThrows(
        IllegalArgumentException.class,
        () -> JitefReader.readRunRequestClassName("{\"className\":123}"));
    assertThrows(
        IllegalArgumentException.class,
        () -> JitefReader.readRunRequestClassName("{\"className\":\"unterminated}"));
    assertThrows(
        IllegalArgumentException.class, () -> JitefReader.readRunRequestFilteredMethods("{}"));
    assertThrows(
        IllegalArgumentException.class,
        () -> JitefReader.readRunRequestFilteredMethods("{\"filteredMethods\":123}"));
    assertThrows(
        IllegalArgumentException.class,
        () -> JitefReader.readRunRequestFilteredMethods("{\"filteredMethods\":\"not [array]\"}"));
  }

  @Test
  public void eventFieldsThrowWhenMissing() {
    assertThrows(IllegalArgumentException.class, () -> JitefReader.readEventType("{}"));
    assertThrows(IllegalArgumentException.class, () -> JitefReader.readEventClass("{}"));
    assertThrows(IllegalArgumentException.class, () -> JitefReader.readEventMethod("{}"));
    assertThrows(IllegalArgumentException.class, () -> JitefReader.readEventThrowableClass("{}"));
    assertThrows(IllegalArgumentException.class, () -> JitefReader.readEventMessage("{}"));
    assertThrows(IllegalArgumentException.class, () -> JitefReader.readEventStack("{}"));
  }

  @Test
  public void readsEscapedControlCharactersFromRunRequestClassName() {
    String payload =
        "{\"bundleId\":1,\"className\":\"a\\rb\\tc\\bd\\fe\\/f\\u0041\",\"filteredMethods\":[\"m\"]}";

    assertEquals("a\rb\tc\bd\fe/fA", JitefReader.readRunRequestClassName(payload));
  }

  @Test
  public void throwsWhenUnicodeEscapeIsMalformed() {
    String payload = "{\"bundleId\":1,\"className\":\"bad \\u00ZZ\",\"filteredMethods\":[\"m\"]}";

    assertThrows(IllegalArgumentException.class, () -> JitefReader.readRunRequestClassName(payload));
  }

  @Test
  public void throwsWhenFilteredMethodsArrayIsUnterminated() {
    String payload = "{\"bundleId\":1,\"className\":\"A\",\"filteredMethods\":[\"m\"}";

    assertThrows(IllegalArgumentException.class, () -> JitefReader.readRunRequestFilteredMethods(payload));
  }
}


