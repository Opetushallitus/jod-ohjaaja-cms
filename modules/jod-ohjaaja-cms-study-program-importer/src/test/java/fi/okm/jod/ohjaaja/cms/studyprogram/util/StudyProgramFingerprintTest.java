/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.studyprogram.util;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

/** Unit tests for the study program fingerprint. */
public class StudyProgramFingerprintTest {

  private static final String STRUCTURE_DEFINITION =
      "{\"fields\":[\"ingress\",\"content\",\"FieldsetLink\"]}";
  private static final String FIELD_SET_DEFINITION = "{\"fields\":[\"linktext\",\"linkurl\"]}";
  private static final Locale FINNISH = Locale.forLanguageTag("fi-FI");
  private static final Locale SWEDISH = Locale.forLanguageTag("sv-SE");

  @Test
  public void testSameValuesGiveSameFingerprint() {
    var title = new LinkedHashMap<Locale, String>();
    title.put(FINNISH, "Koulutus");
    title.put(SWEDISH, "Utbildning");
    var reorderedTitle = new LinkedHashMap<Locale, String>();
    reorderedTitle.put(SWEDISH, "Utbildning");
    reorderedTitle.put(FINNISH, "Koulutus");

    Assert.assertEquals(
        fingerprint(values(title, Map.of(FINNISH, "Ingressi."))),
        fingerprint(values(reorderedTitle, Map.of(FINNISH, "Ingressi."))));
  }

  @Test
  public void testEveryValueChangesFingerprint() throws ReflectiveOperationException {
    // Fails if a value is added to the article values without adding it to the fingerprint
    var values = values(Map.of(FINNISH, "Koulutus"), Map.of(FINNISH, "Ingressi."));
    var components = StudyProgramArticleValues.class.getRecordComponents();
    var constructor =
        StudyProgramArticleValues.class.getDeclaredConstructor(
            Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new));

    for (int changed = 0; changed < components.length; changed++) {
      var arguments = new Object[components.length];
      for (int i = 0; i < components.length; i++) {
        var value = components[i].getAccessor().invoke(values);
        arguments[i] = i == changed ? changedValue(value) : value;
      }

      Assert.assertNotEquals(
          "Changing " + components[changed].getName() + " should change the fingerprint",
          fingerprint(values),
          fingerprint(constructor.newInstance(arguments)));
    }
  }

  @Test
  public void testStructureChangesFingerprint() {
    var values = values(Map.of(FINNISH, "Koulutus"), Map.of(FINNISH, "Ingressi."));

    Assert.assertNotEquals(
        fingerprint(values),
        StudyProgramFingerprint.calculate(
            values,
            List.of(
                "{\"fields\":[\"ingress\",\"content\",\"image\",\"FieldsetLink\"]}",
                FIELD_SET_DEFINITION)));
  }

  @Test
  public void testFieldSetChangesFingerprint() {
    // The definition of the structure only refers to the field set, so it does not change
    var values = values(Map.of(FINNISH, "Koulutus"), Map.of(FINNISH, "Ingressi."));

    Assert.assertNotEquals(
        fingerprint(values),
        StudyProgramFingerprint.calculate(
            values,
            List.of(
                STRUCTURE_DEFINITION, "{\"fields\":[\"linktext\",\"linkurl\",\"linktitle\"]}")));
  }

  @Test
  public void testValuesDoNotRunIntoEachOther() {
    Assert.assertNotEquals(
        fingerprint(values(Map.of(FINNISH, "ab"), Map.of(FINNISH, "c"))),
        fingerprint(values(Map.of(FINNISH, "a"), Map.of(FINNISH, "bc"))));
    Assert.assertNotEquals(
        fingerprint(values(Map.of(FINNISH, "a", SWEDISH, "b"), Map.of())),
        fingerprint(values(Map.of(FINNISH, "a"), Map.of(SWEDISH, "b"))));
  }

  private static String fingerprint(StudyProgramArticleValues values) {
    return StudyProgramFingerprint.calculate(
        values, List.of(STRUCTURE_DEFINITION, FIELD_SET_DEFINITION));
  }

  private static StudyProgramArticleValues values(
      Map<Locale, String> title, Map<Locale, String> ingress) {
    return new StudyProgramArticleValues(
        "oid",
        title,
        ingress,
        Map.of(FINNISH, "Koulutuksen kuvaus."),
        Map.of(FINNISH, "https://opintopolku.fi/konfo/fi/koulutus/oid"),
        "https://example.com/a.jpg");
  }

  private static Object changedValue(Object value) {
    if (value instanceof String string) {
      return string + "x";
    }
    if (value instanceof Map<?, ?> map) {
      var changedMap = new HashMap<Object, Object>(map);
      changedMap.put(FINNISH, map.get(FINNISH) + "x");
      return changedMap;
    }
    throw new IllegalArgumentException("No change defined for the value " + value);
  }
}
