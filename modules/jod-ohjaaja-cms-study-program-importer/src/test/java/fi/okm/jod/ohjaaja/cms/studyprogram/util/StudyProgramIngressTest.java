/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.studyprogram.util;

import org.junit.Assert;
import org.junit.Test;

/** Unit tests for extracting the ingress of a study program article. */
public class StudyProgramIngressTest {

  @Test
  public void testTakesFullSentencesUpToMaxLength() {
    var first = "Opiskele ohjausta.";
    var second = "Koulutus antaa valmiudet ohjaustyöhön.";
    var third = "Kolmas lause ei mahdu ingressiin, koska se on liian pitkä.";
    Assert.assertEquals(
        first + " " + second,
        StudyProgramIngress.extract("  " + first + " " + second + " " + third));
  }

  @Test
  public void testTakesFirstSentenceEvenIfLonger() {
    var longSentence =
        "Tämä lause on pidempi kuin kahdeksankymmentä merkkiä, joten se otetaan ingressiin kokonaan.";
    Assert.assertEquals(longSentence, StudyProgramIngress.extract(longSentence + " Toinen."));
  }

  @Test
  public void testTakesWordsUpToMaxLengthWithoutSentences() {
    var text = "sana ".repeat(30).trim();
    var ingress = StudyProgramIngress.extract(text);
    Assert.assertEquals("sana ".repeat(16).trim(), ingress);
  }

  @Test
  public void testTakesSingleLongWordWhole() {
    var word = "a".repeat(100);
    Assert.assertEquals(word, StudyProgramIngress.extract(word));
  }

  @Test
  public void testReturnsEmptyIngressForEmptyText() {
    Assert.assertEquals("", StudyProgramIngress.extract("  "));
  }
}
