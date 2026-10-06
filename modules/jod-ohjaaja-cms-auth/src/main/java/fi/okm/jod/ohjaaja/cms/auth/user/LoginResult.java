/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.auth.user;

import com.liferay.portal.kernel.model.User;

/** Outcome of resolving the Liferay user of a verified Cognito identity. */
public sealed interface LoginResult {

  /** Why a verified Cognito identity is not logged in. */
  enum DenialReason {
    /** The identity has no Cognito group that is mapped to a CMS role. */
    NO_ACCESS,
    /**
     * The identity has CMS groups, but its Liferay user cannot be used: the email is unverified or
     * linked to another Cognito user, the user is deactivated or it is the default admin.
     */
    ACCOUNT_CONFLICT
  }

  record Allowed(User user) implements LoginResult {}

  record Denied(DenialReason reason) implements LoginResult {}
}
