/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.statistics.view;

import java.time.LocalDate;
import java.util.List;

public record StatisticsView(
    LocalDate alku,
    LocalDate loppu,
    long registeredUsers,
    List<DistributionRow> workplaces,
    List<DistributionRow> interests,
    List<ArticleRow> mostFavorited,
    List<ArticleRow> mostCommented,
    List<ArticleRow> mostViewed) {}
