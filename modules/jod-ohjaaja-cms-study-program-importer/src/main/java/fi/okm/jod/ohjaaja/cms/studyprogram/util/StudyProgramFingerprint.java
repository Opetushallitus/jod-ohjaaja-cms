/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.studyprogram.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Fingerprint of the values that a study program article is built from. */
public final class StudyProgramFingerprint {

  /**
   * Part of every fingerprint. A change in how the {@link StudyProgramArticleValues} are derived
   * from the study program data changes the fingerprint anyway. Increase this only when the way the
   * values are placed in the article content, which {@link StudyProgramArticleFields} does,
   * changes, so that all articles are updated on the next import.
   */
  public static final int VERSION = 1;

  private StudyProgramFingerprint() {}

  /**
   * Calculates the fingerprint of the given article values.
   *
   * @param structureDefinitions the definitions of the DDM structure of the article and the field
   *     sets that it uses
   */
  public static String calculate(
      StudyProgramArticleValues values, List<String> structureDefinitions) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      update(digest, String.valueOf(VERSION));
      update(digest, String.valueOf(structureDefinitions.size()));
      structureDefinitions.forEach(definition -> update(digest, definition));
      update(digest, values.oid());
      update(digest, values.title());
      update(digest, values.ingress());
      update(digest, values.content());
      update(digest, values.linkUrl());
      update(digest, values.imageUrl());
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  private static void update(MessageDigest digest, Map<Locale, String> values) {
    // The values are sorted by language so that the order of the map does not matter, and prefixed
    // with their count so that the values of two maps cannot run into each other
    var sortedValues = new TreeMap<String, String>();
    values.forEach((locale, value) -> sortedValues.put(locale.toLanguageTag(), value));
    update(digest, String.valueOf(sortedValues.size()));
    sortedValues.forEach(
        (languageTag, value) -> {
          update(digest, languageTag);
          update(digest, value);
        });
  }

  private static void update(MessageDigest digest, String value) {
    // A null value is told apart from an empty one, and every value is prefixed with its length
    // so that values cannot run into each other
    if (value == null) {
      digest.update((byte) 0);
      return;
    }
    var bytes = value.getBytes(StandardCharsets.UTF_8);
    digest.update((byte) 1);
    digest.update(String.valueOf(bytes.length).getBytes(StandardCharsets.UTF_8));
    digest.update((byte) ':');
    digest.update(bytes);
  }
}
