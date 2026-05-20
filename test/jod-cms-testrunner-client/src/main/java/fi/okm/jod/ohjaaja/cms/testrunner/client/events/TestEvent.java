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

/** One event parsed from the container's NDJITEF output stream. */
public record TestEvent(Type type, String className, String methodName, String throwableClass,
                        String message, String stack) {

  /** Event types. The on-the-wire string is the lowerCamelCase form (e.g. {@code started}). */
  public enum Type {
    STARTED,
    FINISHED,
    FAILURE,
    ASSUMPTION_FAILURE,
    IGNORED,
    RUN_ERROR;

    public static Type from(String wire) {
      if (wire == null) {
        throw new IllegalArgumentException("Missing required event field 'type'");
      }
      return switch (wire) {
        case "started" -> STARTED;
        case "finished" -> FINISHED;
        case "failure" -> FAILURE;
        case "assumptionFailure" -> ASSUMPTION_FAILURE;
        case "ignored" -> IGNORED;
        case "runError" -> RUN_ERROR;
        default -> throw new IllegalArgumentException("Unknown event type: " + wire);
      };
    }
  }

  @Override
  public String toString() {
    return "TestEvent{type=" + type + ", class=" + className + ", method=" + methodName + "}";
  }
}
