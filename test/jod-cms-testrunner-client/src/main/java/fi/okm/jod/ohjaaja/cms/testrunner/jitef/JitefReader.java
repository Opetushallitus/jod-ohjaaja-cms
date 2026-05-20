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

import java.util.ArrayList;
import java.util.List;

/**
 * Reader for JodCmsIntegrationTestExchangeFormat (JITEF) payloads.
 *
 * <p>JITEF is the internal wire format of the JOD CMS integration test runner. The syntax is
 * intentionally JSON-like for readability and NDJITEF streaming, but it is still a protocol
 * format, not a general JSON API contract.
 *
 * <p>Public methods are protocol-specific accessors for the payloads produced by
 * {@link JitefWriter} and consumed by runtime components. Generic field readers remain private so
 * callers cannot parse arbitrary shapes through this class.
 *
 * <p>Required protocol fields are parsed strictly. Missing or malformed required fields cause an
 * {@link IllegalArgumentException}. Optional fields are handled by caller-specific logic.
 */
public final class JitefReader {

  private static final String FIELD_TYPE = "type";
  private static final String FIELD_CLASS = "class";
  private static final String FIELD_METHOD = "method";
  private static final String FIELD_THROWABLE_CLASS = "throwableClass";
  private static final String FIELD_MESSAGE = "message";
  private static final String FIELD_STACK = "stack";
  private static final String FIELD_BUNDLE_ID = "bundleId";
  private static final String FIELD_CLASS_NAME = "className";
  private static final String FIELD_FILTERED_METHODS = "filteredMethods";

  private JitefReader() {}

  /** Returns bundle id from the install-bundle response payload. */
  public static long readInstallResponseBundleId(String payload) {
    return requiredLongField(payload, FIELD_BUNDLE_ID);
  }

  /** Returns bundle id from a run-request payload. */
  public static long readRunRequestBundleId(String payload) {
    return requiredLongField(payload, FIELD_BUNDLE_ID);
  }

  /** Returns class name from a run-request payload. */
  public static String readRunRequestClassName(String payload) {
    return requiredStringField(payload, FIELD_CLASS_NAME);
  }

  /** Returns filtered method names from a run-request payload. */
  public static List<String> readRunRequestFilteredMethods(String payload) {
    return stringArrayField(payload, FIELD_FILTERED_METHODS);
  }

  /** Returns event type from an NDJITEF event payload. */
  public static String readEventType(String payload) {
    return requiredStringField(payload, FIELD_TYPE);
  }

  /** Returns class from an NDJITEF event payload. */
  public static String readEventClass(String payload) {
    return requiredStringField(payload, FIELD_CLASS);
  }

  /** Returns method from an NDJITEF event payload. */
  public static String readEventMethod(String payload) {
    return requiredStringField(payload, FIELD_METHOD);
  }

  /** Returns throwable class from an NDJITEF event payload. */
  public static String readEventThrowableClass(String payload) {
    return requiredStringField(payload, FIELD_THROWABLE_CLASS);
  }

  /** Returns message from an NDJITEF event payload. */
  public static String readEventMessage(String payload) {
    return requiredStringField(payload, FIELD_MESSAGE);
  }

  /** Returns stack trace from an NDJITEF event payload. */
  public static String readEventStack(String payload) {
    return requiredStringField(payload, FIELD_STACK);
  }

  private static String requiredStringField(String payload, String name) {
    String value = stringField(payload, name);
    if (value == null) {
      throw invalidRequiredField(name);
    }
    return value;
  }

  private static long requiredLongField(String payload, String name) {
    Long value = longField(payload, name);
    if (value == null) {
      throw invalidRequiredField(name);
    }
    return value;
  }

  private static IllegalArgumentException invalidRequiredField(String name) {
    return new IllegalArgumentException("Missing or invalid required field '" + name + "'");
  }

  private static String stringField(String payload, String name) {
    int colon = fieldColonIndex(payload, name);
    if (colon < 0) {
      return null;
    }
    int i = skipWhitespace(payload, colon + 1);
    if (i >= payload.length() || payload.charAt(i) != '"') {
      return null;
    }
    return readStringStartingAtQuote(payload, i);
  }

  private static Long longField(String payload, String name) {
    int colon = fieldColonIndex(payload, name);
    if (colon < 0) {
      return null;
    }
    int i = skipWhitespace(payload, colon + 1);
    int start = i;
    if (i < payload.length() && (payload.charAt(i) == '-' || payload.charAt(i) == '+')) {
      i++;
    }
    while (i < payload.length() && Character.isDigit(payload.charAt(i))) {
      i++;
    }
    if (start == i || (i - start == 1 && !Character.isDigit(payload.charAt(start)))) {
      return null;
    }
    int next = skipWhitespace(payload, i);
    if (next < payload.length()) {
      char c = payload.charAt(next);
      if (c != ',' && c != '}' && c != ']') {
        return null;
      }
    }
    try {
      return Long.parseLong(payload.substring(start, i));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static List<String> stringArrayField(String payload, String name) {
    int colon = fieldColonIndex(payload, name);
    if (colon < 0) {
      throw invalidRequiredField(name);
    }
    int open = skipWhitespace(payload, colon + 1);
    if (open >= payload.length() || payload.charAt(open) != '[') {
      throw invalidRequiredField(name);
    }
    int close = findArrayEnd(payload, open);
    if (close < 0) {
      throw invalidRequiredField(name);
    }
    return collectArrayStrings(payload, open + 1, close);
  }

  private static List<String> collectArrayStrings(String payload, int from, int close) {
    List<String> result = new ArrayList<>();
    int i = from;
    while (i < close) {
      int next = readNextArrayElement(payload, i, close, result);
      if (next < 0) {
        break;
      }
      i = next;
    }
    return result;
  }

  /**
   * Reads the next element from an array that ends at {@code close}. Strings are decoded into
   * {@code result}; non-string tokens are skipped over. Returns the next index to read from, or
   * {@code -1} when iteration should stop (end of array or malformed input).
   */
  private static int readNextArrayElement(String payload, int from, int close, List<String> result) {
    int i = skipWhitespaceAndCommas(payload, from, close);
    if (i >= close) {
      return -1;
    }
    if (payload.charAt(i) != '"') {
      while (i < close && payload.charAt(i) != ',') {
        i++;
      }
      return i;
    }
    String s = readStringStartingAtQuote(payload, i);
    if (s == null) {
      return -1;
    }
    result.add(s);
    return endOfStringStartingAtQuote(payload, i);
  }

  /**
   * Reads an encoded string that starts at {@code payload.charAt(quoteIndex) == '"'} and returns
   * the decoded value (without surrounding quotes). Returns {@code null} if the input is malformed.
   */
  private static String readStringStartingAtQuote(String payload, int quoteIndex) {
    if (quoteIndex < 0 || quoteIndex >= payload.length() || payload.charAt(quoteIndex) != '"') {
      return null;
    }
    StringBuilder sb = new StringBuilder();
    int len = payload.length();
    int i = quoteIndex + 1;
    while (i < len) {
      char c = payload.charAt(i);
      if (c == '"') {
        return sb.toString();
      }
      if (c == '\\' && i + 1 < len) {
        i = appendEscapedChar(payload, i + 1, sb);
      } else {
        sb.append(c);
        i++;
      }
    }
    return null;
  }

  /**
   * Appends the escaped sequence whose escape character lives at {@code afterBackslash} to
   * {@code sb}, and returns the index after the consumed sequence.
   */
  private static int appendEscapedChar(String payload, int afterBackslash, StringBuilder sb) {
    char next = payload.charAt(afterBackslash);
    return switch (next) {
      case '\\', '"', '/' -> {
        sb.append(next);
        yield afterBackslash + 1;
      }
      case 'n' -> {
        sb.append('\n');
        yield afterBackslash + 1;
      }
      case 'r' -> {
        sb.append('\r');
        yield afterBackslash + 1;
      }
      case 't' -> {
        sb.append('\t');
        yield afterBackslash + 1;
      }
      case 'b' -> {
        sb.append('\b');
        yield afterBackslash + 1;
      }
      case 'f' -> {
        sb.append('\f');
        yield afterBackslash + 1;
      }
      case 'u' -> appendUnicodeEscape(payload, afterBackslash, sb);
      default -> {
        sb.append(next);
        yield afterBackslash + 1;
      }
    };
  }

  private static int appendUnicodeEscape(String payload, int afterBackslash, StringBuilder sb) {
    if (afterBackslash + 4 < payload.length()) {
      sb.append((char) Integer.parseInt(payload.substring(afterBackslash + 1, afterBackslash + 5), 16));
      return afterBackslash + 5;
    }
    return afterBackslash + 1;
  }

  /**
   * Returns the index just past the closing quote of a string that starts at the given index,
   * or {@code -1} if the string is unterminated.
   */
  private static int endOfStringStartingAtQuote(String payload, int quoteIndex) {
    int i = quoteIndex + 1;
    while (i < payload.length()) {
      char c = payload.charAt(i);
      if (c == '\\' && i + 1 < payload.length()) {
        i += 2;
      } else if (c == '"') {
        return i + 1;
      } else {
        i++;
      }
    }
    return -1;
  }

  private static int fieldColonIndex(String payload, String name) {
    String needle = "\"" + name + "\"";
    int from = 0;
    while (true) {
      int idx = payload.indexOf(needle, from);
      if (idx < 0) {
        return -1;
      }
      int j = skipWhitespace(payload, idx + needle.length());
      if (j < payload.length() && payload.charAt(j) == ':') {
        return j;
      }
      from = idx + needle.length();
    }
  }

  /** Returns the index of the matching closing bracket, or {@code -1} if the array is malformed. */
  private static int findArrayEnd(String payload, int openBracket) {
    int len = payload.length();
    int i = openBracket;
    int depth = 0;
    while (i < len) {
      char c = payload.charAt(i);
      if (c == '"') {
        i = endOfStringStartingAtQuote(payload, i);
        if (i < 0) {
          return -1;
        }
        continue;
      }
      if (c == '[') {
        depth++;
      } else if (c == ']' && --depth == 0) {
        return i;
      }
      i++;
    }
    return -1;
  }

  private static int skipWhitespace(String payload, int from) {
    int i = from;
    while (i < payload.length() && Character.isWhitespace(payload.charAt(i))) {
      i++;
    }
    return i;
  }

  private static int skipWhitespaceAndCommas(String payload, int from, int limit) {
    int i = from;
    while (i < limit && (Character.isWhitespace(payload.charAt(i)) || payload.charAt(i) == ',')) {
      i++;
    }
    return i;
  }
}


