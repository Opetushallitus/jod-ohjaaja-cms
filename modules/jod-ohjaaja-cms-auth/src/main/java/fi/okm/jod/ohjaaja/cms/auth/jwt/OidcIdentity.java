/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.auth.jwt;

import java.util.List;

/**
 * Verified identity of a user authenticated by the ALB against Cognito.
 *
 * <p>{@code emailVerified} tells whether Cognito has verified the ownership of {@code email}. The
 * ALB signature only proves the origin of the claims, not the ownership of the email address.
 */
public record OidcIdentity(
    String sub,
    String email,
    boolean emailVerified,
    String givenName,
    String familyName,
    List<String> groups) {}
