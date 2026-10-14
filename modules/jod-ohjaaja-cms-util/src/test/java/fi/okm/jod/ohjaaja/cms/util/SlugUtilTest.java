/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.util;

import org.junit.Assert;
import org.junit.Test;

/**
 * The slugs of existing pages must not change. ß, Æ and Ø give poor slugs, as they are not letters
 * with accents, but changing them would break existing URLs.
 */
public class SlugUtilTest {

  private static final String[][] TEST_VECTORS = {
    {"Test String", "test-string"},
    {"hello   world", "hello-world"},
    {"héllö wörld", "hello-world"},
    {"Ääkköset ja Åbo", "aakkoset-ja-abo"},
    {"Café", "cafe"},
    {"Ångström", "angstrom"},
    {"İstanbul", "istanbul"},
    {"Straße", "stra-e"},
    {"Æsir Øre", "sir-re"},
    {"Don´t", "don-t"},
    {"a^b", "a-b"},
    {"½ vuotta", "vuotta"},
    {"Oikeus osata – Nivus", "oikeus-osata-nivus"},
    {"Kotoutumisentukena.fi", "kotoutumisentukena-fi"},
    {"Opinto-ohjaajan erilliset opinnot (60 op)", "opinto-ohjaajan-erilliset-opinnot-60-op"},
    {"special!@#$chars", "special-chars"},
    {"hello--world", "hello-world"},
    {"-leading-trailing-", "leading-trailing"},
    {"\tÄäni\nja\r\nkuva\t", "aani-ja-kuva"},
    {"\u00A0Ääni\u00A0ja\u2009kuva\u2003", "aani-ja-kuva"},
    {"Ää\u00ADni", "aani"},
    {"Ää\u200Bni", "aani"},
    {"\uFEFFÄäni", "aani"},
    {"   ", ""},
    {"", ""},
  };

  @Test
  public void testSluggify() {
    for (var testVector : TEST_VECTORS) {
      Assert.assertEquals(testVector[0], testVector[1], SlugUtil.sluggify(testVector[0]));
    }
  }

  @Test
  public void testNullIsEmpty() {
    Assert.assertEquals("", SlugUtil.sluggify(null));
  }
}
