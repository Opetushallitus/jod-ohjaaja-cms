/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.statistics.client.exception;

public class StatisticsApiException extends Exception {
  public StatisticsApiException(String message) {
    super(message);
  }

  public StatisticsApiException(Throwable cause) {
    super(cause);
  }
}
