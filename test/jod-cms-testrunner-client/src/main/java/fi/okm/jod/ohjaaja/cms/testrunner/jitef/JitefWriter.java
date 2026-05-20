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

/**
 * Writer for JodCmsIntegrationTestExchangeFormat (JITEF) payloads.
 *
 * <p>JITEF is the internal exchange format of the JOD CMS integration test runner. Its syntax is
 * JSON-like for readability, but it is intentionally a narrow protocol format rather than a
 * general JSON API contract. NDJITEF streams are emitted as one JITEF object per line.
 *
 * <p>Public methods map directly to supported protocol payload shapes (run request and test
 * events). The generic object/field builders are private on purpose, so callers cannot construct
 * unsupported payload variants by accident.
 *
 * <p>Writer and {@link JitefReader} are designed as a pair: this class produces the exact shape
 * that reader-side protocol accessors consume.
 */
public final class JitefWriter {

  private static final String FIELD_TYPE = "type";
  private static final String FIELD_CLASS = "class";
  private static final String FIELD_METHOD = "method";
  private static final String FIELD_THROWABLE_CLASS = "throwableClass";
  private static final String FIELD_MESSAGE = "message";
  private static final String FIELD_STACK = "stack";
  private static final String FIELD_BUNDLE_ID = "bundleId";
  private static final String FIELD_CLASS_NAME = "className";
  private static final String FIELD_FILTERED_METHODS = "filteredMethods";
  private static final String FIELD_STATUS = "status";
  private static final String FIELD_ERROR = "error";
  private static final String FIELD_DETAIL = "detail";

  private final StringBuilder buffer = new StringBuilder();
  private boolean firstField = true;

  private JitefWriter() {}

  /** Serializes the protocol payload used by {@code POST /run}. */
  public static String runRequest(long bundleId, String className, Iterable<String> filteredMethods) {
    return new JitefWriter()
        .beginObject()
        .field(FIELD_BUNDLE_ID, bundleId)
        .field(FIELD_CLASS_NAME, className)
        .field(FIELD_FILTERED_METHODS, filteredMethods)
        .endObject()
        .toPayload();
  }

  /** Serializes the response payload returned by {@code POST /bundles}. */
  public static String installBundleResponse(long bundleId) {
    return new JitefWriter().beginObject().field(FIELD_BUNDLE_ID, bundleId).endObject().toPayload();
  }

  /** Serializes the health response payload returned by {@code GET /ping}. */
  public static String statusOkResponse() {
    return new JitefWriter().beginObject().field(FIELD_STATUS, "ok").endObject().toPayload();
  }

  /**
   * Serializes a run-level error event emitted to the NDJITEF stream. {@code message} and
   * {@code throwableClass} are required protocol fields; null inputs are normalised to empty
   * strings so the host's strict reader does not reject the stream when an exception's
   * {@link Throwable#getMessage()} happens to be null.
   */
  public static String runErrorEvent(String message, String throwableClass) {
    return new JitefWriter()
        .beginObject()
        .field(FIELD_TYPE, "runError")
        .field(FIELD_MESSAGE, nullToEmpty(message))
        .field(FIELD_THROWABLE_CLASS, nullToEmpty(throwableClass))
        .endObject()
        .toPayload();
  }

  /** Serializes an HTTP error response payload. */
  public static String errorResponse(String error, String detail) {
    return new JitefWriter()
        .beginObject()
        .field(FIELD_ERROR, error)
        .field(FIELD_DETAIL, detail)
        .endObject()
        .toPayload();
  }

  /** Serializes a protocol event that only carries type/class/method fields. */
  public static String testEvent(String type, String className, String methodName) {
    return new JitefWriter()
        .beginObject()
        .field(FIELD_TYPE, type)
        .field(FIELD_CLASS, className)
        .field(FIELD_METHOD, methodName)
        .endObject()
        .toPayload();
  }

  /**
   * Serializes a protocol event that carries failure details. All string fields are required by
   * the host reader, so null inputs are normalised to empty strings to keep the stream parseable
   * even for class-level failures (where method/class can legitimately be null).
   */
  public static String testFailureEvent(
      String type,
      String className,
      String methodName,
      String throwableClass,
      String message,
      String stack) {
    return new JitefWriter()
        .beginObject()
        .field(FIELD_TYPE, type)
        .field(FIELD_CLASS, nullToEmpty(className))
        .field(FIELD_METHOD, nullToEmpty(methodName))
        .field(FIELD_THROWABLE_CLASS, nullToEmpty(throwableClass))
        .field(FIELD_MESSAGE, nullToEmpty(message))
        .field(FIELD_STACK, nullToEmpty(stack))
        .endObject()
        .toPayload();
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private JitefWriter beginObject() {
    buffer.append('{');
    firstField = true;
    return this;
  }

  private JitefWriter endObject() {
    buffer.append('}');
    return this;
  }

  private JitefWriter field(String name, String value) {
    appendFieldName(name);
    if (value == null) {
      buffer.append("null");
    } else {
      appendString(value);
    }
    return this;
  }

  private JitefWriter field(String name, long value) {
    appendFieldName(name);
    buffer.append(value);
    return this;
  }

  /** Writes a JITEF string-array field. A {@code null} iterable is encoded as an empty array. */
  private JitefWriter field(String name, Iterable<String> values) {
    appendFieldName(name);
    buffer.append('[');
    if (values != null) {
      boolean first = true;
      for (String value : values) {
        if (!first) {
          buffer.append(',');
        }
        if (value == null) {
          buffer.append("null");
        } else {
          appendString(value);
        }
        first = false;
      }
    }
    buffer.append(']');
    return this;
  }

  private void appendFieldName(String name) {
    if (!firstField) {
      buffer.append(',');
    }
    firstField = false;
    appendString(name);
    buffer.append(':');
  }

  private void appendString(String s) {
    buffer.append('"');
    appendEscaped(buffer, s);
    buffer.append('"');
  }

  private static void appendEscaped(StringBuilder sb, String s) {
    if (s == null) {
      return;
    }
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '\\':
          sb.append("\\\\");
          break;
        case '"':
          sb.append("\\\"");
          break;
        case '\n':
          sb.append("\\n");
          break;
        case '\r':
          sb.append("\\r");
          break;
        case '\t':
          sb.append("\\t");
          break;
        case '\b':
          sb.append("\\b");
          break;
        case '\f':
          sb.append("\\f");
          break;
        default:
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
      }
    }
  }

  private String toPayload() {
    return buffer.toString();
  }
}

