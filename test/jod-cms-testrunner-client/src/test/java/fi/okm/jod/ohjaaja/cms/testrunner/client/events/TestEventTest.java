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
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class TestEventTest {

  @Test
  public void mapsAllKnownWireTypes() {
    assertEquals(TestEvent.Type.STARTED, TestEvent.Type.from("started"));
    assertEquals(TestEvent.Type.FINISHED, TestEvent.Type.from("finished"));
    assertEquals(TestEvent.Type.FAILURE, TestEvent.Type.from("failure"));
    assertEquals(TestEvent.Type.ASSUMPTION_FAILURE, TestEvent.Type.from("assumptionFailure"));
    assertEquals(TestEvent.Type.IGNORED, TestEvent.Type.from("ignored"));
    assertEquals(TestEvent.Type.RUN_ERROR, TestEvent.Type.from("runError"));
  }

  @Test
  public void throwsOnMissingOrUnknownWireType() {
    assertThrows(IllegalArgumentException.class, () -> TestEvent.Type.from(null));
    assertThrows(IllegalArgumentException.class, () -> TestEvent.Type.from("mystery"));
  }

  @Test
  public void toStringContainsTypeClassAndMethod() {
    TestEvent event = new TestEvent(TestEvent.Type.STARTED, "A", "m", null, null, null);

    assertEquals("TestEvent{type=STARTED, class=A, method=m}", event.toString());
  }
}

