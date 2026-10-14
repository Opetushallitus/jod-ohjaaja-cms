/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.sitemap.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

/** Dates of the sitemap are days in Finnish time. */
final class SitemapDates {

  private static final ZoneId TIME_ZONE = ZoneId.of("Europe/Helsinki");

  private SitemapDates() {}

  /**
   * Via epoch millis, as a java.sql.Date or Timestamp from the database does not support
   * toInstant() in every case.
   */
  static LocalDate toLocalDate(Date date) {
    return date != null
        ? LocalDate.ofInstant(Instant.ofEpochMilli(date.getTime()), TIME_ZONE)
        : null;
  }
}
