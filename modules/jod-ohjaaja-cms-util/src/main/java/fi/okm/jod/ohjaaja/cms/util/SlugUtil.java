/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.util;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * URL slugs of navigation items. The navigation API returns them to jod-ohjaaja-ui, which builds
 * its routes from them, and the sitemap uses the same slugs, so this is the only implementation.
 *
 * <p>A change here changes the URLs of existing pages and breaks links to them.
 */
public final class SlugUtil {

  // Accents separated by NFD, and invisible format characters such as soft hyphens and zero-width
  // spaces, which would otherwise split a word
  private static final Pattern REMOVED_CHARACTERS = Pattern.compile("[\\p{M}\\p{Cf}]+");
  private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
  private static final Pattern EDGE_DASHES = Pattern.compile("(?:^-)|(?:-$)");

  private SlugUtil() {}

  /** The slugs of localized names, e.g. {"fi-FI": "Ääni"} becomes {"fi-FI": "aani"}. */
  public static Map<String, String> sluggifyAll(Map<String, String> localizedValues) {
    var slugs = new LinkedHashMap<String, String>();
    localizedValues.forEach((key, value) -> slugs.put(key, sluggify(value)));
    return slugs;
  }

  /** E.g. "Ääkköset ja Åbo" becomes "aakkoset-ja-abo". */
  public static String sluggify(String value) {
    if (value == null) {
      return "";
    }
    // NFD separates the accents from the letters, so that ä becomes a
    var withoutAccents =
        REMOVED_CHARACTERS.matcher(Normalizer.normalize(value, Normalizer.Form.NFD)).replaceAll("");
    var slug = NON_ALPHANUMERIC.matcher(withoutAccents.toLowerCase(Locale.ROOT)).replaceAll("-");
    return EDGE_DASHES.matcher(slug).replaceAll("");
  }
}
