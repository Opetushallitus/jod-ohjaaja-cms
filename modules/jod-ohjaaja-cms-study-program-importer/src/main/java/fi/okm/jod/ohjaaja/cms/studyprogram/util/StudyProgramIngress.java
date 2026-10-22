/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.studyprogram.util;

/** The ingress (introductory text) of a study program article. */
public final class StudyProgramIngress {

  private static final int MAX_LENGTH = 80;
  private static final String SENTENCE_ENDS = ".!?";

  private StudyProgramIngress() {}

  /**
   * Extracts an ingress from the given text by taking full sentences until reaching approximately
   * 80 characters, or words if it has no full sentences.
   *
   * @param text the input text, without HTML tags
   */
  public static String extract(String text) {
    var stripped = text.trim();
    var ingress = new StringBuilder();
    int sentenceStart = 0;
    for (int i = 0; i < stripped.length(); i++) {
      if (SENTENCE_ENDS.indexOf(stripped.charAt(i)) >= 0) {
        if (!append(ingress, stripped.substring(sentenceStart, i + 1).trim())) {
          break;
        }
        sentenceStart = i + 1;
      }
    }
    if (ingress.isEmpty()) {
      for (var word : stripped.split("\\s+")) {
        if (!append(ingress, word)) {
          break;
        }
      }
    }
    // Also removes control characters that a word can end with
    return ingress.toString().trim();
  }

  /**
   * Appends the given part to the ingress, separated by a space, unless that would make the ingress
   * longer than {@link #MAX_LENGTH}. The first part is always appended, however long it is.
   *
   * @return false if the part was not appended
   */
  private static boolean append(StringBuilder ingress, String part) {
    if (ingress.isEmpty()) {
      ingress.append(part);
      return true;
    }
    if (ingress.length() + 1 + part.length() > MAX_LENGTH) {
      return false;
    }
    ingress.append(' ').append(part);
    return true;
  }
}
