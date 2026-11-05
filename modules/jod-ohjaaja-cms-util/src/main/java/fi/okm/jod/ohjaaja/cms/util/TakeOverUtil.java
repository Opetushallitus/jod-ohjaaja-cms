/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.util;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;

/**
 * The common rules for taking over content created by hand before it was managed in code: such
 * content is found by its former external reference code, name or title, and given the external
 * reference code of the code.
 */
public final class TakeOverUtil {

  /** The prefix of the external reference codes of the content managed in code. */
  public static final String MANAGED_EXTERNAL_REFERENCE_CODE_PREFIX = "jod-ohjaaja-cms-";

  private TakeOverUtil() {}

  /** Whether the content is already managed in code, so it must not be taken over as another. */
  public static boolean isManaged(String externalReferenceCode) {
    return externalReferenceCode != null
        && externalReferenceCode.startsWith(MANAGED_EXTERNAL_REFERENCE_CODE_PREFIX);
  }

  /** Whether any title of the content, in any language, is one of the given titles. */
  public static boolean hasAnyTitle(Map<Locale, String> titleMap, Collection<String> titles) {
    return titleMap.values().stream().anyMatch(titles::contains);
  }
}
