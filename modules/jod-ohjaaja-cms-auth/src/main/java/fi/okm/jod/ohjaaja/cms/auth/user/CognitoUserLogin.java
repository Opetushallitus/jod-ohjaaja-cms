/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.auth.user;

import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.model.UserConstants;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.PropsKeys;
import com.liferay.portal.kernel.util.PropsUtil;
import com.liferay.portal.kernel.util.Validator;
import fi.okm.jod.ohjaaja.cms.auth.jwt.OidcIdentity;
import fi.okm.jod.ohjaaja.cms.auth.role.RoleMapping;
import fi.okm.jod.ohjaaja.cms.auth.role.RoleSynchronizer;
import fi.okm.jod.ohjaaja.cms.auth.user.LoginResult.Allowed;
import fi.okm.jod.ohjaaja.cms.auth.user.LoginResult.DenialReason;
import fi.okm.jod.ohjaaja.cms.auth.user.LoginResult.Denied;
import fi.okm.jod.ohjaaja.cms.util.JodOhjaajaCmsUtil;
import java.util.Objects;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Resolves the Liferay user of a verified Cognito identity.
 *
 * <p>Users are looked up by Cognito subject (stored as the external reference code) and created
 * when missing. An existing user that has not yet been linked to Cognito (its external reference
 * code is still the Liferay default, the UUID) is linked once by email address, but only when
 * Cognito has verified the email. A user already linked to another Cognito subject is never
 * relinked. Mapped roles are synchronized from the Cognito groups on every login. Users without any
 * mapped group are not logged in, and their mapped roles are removed. The default admin account is
 * never resolved, it stays as a separate emergency account.
 */
@Component(service = CognitoUserLogin.class)
public class CognitoUserLogin {

  private static final Log log = LogFactoryUtil.getLog(CognitoUserLogin.class);

  @Reference private UserLocalService userLocalService;
  @Reference private RoleSynchronizer roleSynchronizer;
  @Reference private JodOhjaajaCmsUtil jodOhjaajaCmsUtil;

  /** Returns the user to log in, or the reason why the identity is not allowed to log in. */
  public LoginResult login(long companyId, OidcIdentity identity) throws PortalException {
    if (RoleMapping.resolve(identity.groups()).isEmpty()) {
      log.info("Cognito user " + identity.sub() + " has no CMS groups, not logging in");
      revokeMappedRoles(companyId, identity);
      return new Denied(DenialReason.NO_ACCESS);
    }
    var user = getOrCreateUser(companyId, identity);
    if (user == null) {
      return new Denied(DenialReason.ACCOUNT_CONFLICT);
    }
    roleSynchronizer.synchronize(user, identity.groups());
    return new Allowed(user);
  }

  private void revokeMappedRoles(long companyId, OidcIdentity identity) throws PortalException {
    var user = userLocalService.fetchUserByExternalReferenceCode(identity.sub(), companyId);
    if (user != null && !isDefaultAdmin(user)) {
      roleSynchronizer.synchronize(user, identity.groups());
    }
  }

  private User getOrCreateUser(long companyId, OidcIdentity identity) throws PortalException {
    var user = userLocalService.fetchUserByExternalReferenceCode(identity.sub(), companyId);
    if (user == null) {
      user = userLocalService.fetchUserByEmailAddress(companyId, identity.email());
      if (user != null && !isDefaultAdmin(user)) {
        if (!identity.emailVerified()) {
          log.warn(
              "Email of Cognito user "
                  + identity.sub()
                  + " is not verified, not linking it to user "
                  + user.getUserId());
          return null;
        }
        if (!isUnlinked(user)) {
          log.warn(
              "User "
                  + user.getUserId()
                  + " with the email of Cognito user "
                  + identity.sub()
                  + " is already linked to another Cognito user, not logging in");
          return null;
        }
        log.info("Linking user " + user.getUserId() + " to Cognito user " + identity.sub());
        user = userLocalService.updateExternalReferenceCode(user, identity.sub());
      }
    }
    if (user == null) {
      return addUser(companyId, identity);
    }
    if (isDefaultAdmin(user)) {
      log.warn("Refusing to log in the default admin user with Cognito user " + identity.sub());
      return null;
    }
    if (!user.isActive()) {
      log.info("User " + user.getUserId() + " is deactivated, not logging in");
      return null;
    }
    return updateUser(companyId, user, identity);
  }

  private User addUser(long companyId, OidcIdentity identity) throws PortalException {
    var serviceContext = new ServiceContext();
    serviceContext.setCompanyId(companyId);
    var user =
        userLocalService.addUserWithWorkflow(
            UserConstants.USER_ID_DEFAULT,
            companyId,
            true,
            null,
            null,
            true,
            null,
            identity.email(),
            LocaleUtil.fromLanguageId("fi_FI"),
            firstName(identity),
            null,
            lastName(identity),
            0,
            0,
            true,
            1,
            1,
            1970,
            null,
            UserConstants.TYPE_REGULAR,
            new long[] {jodOhjaajaCmsUtil.getJodOhjaajaCmsGroup().getGroupId()},
            null,
            null,
            null,
            false,
            serviceContext);
    // The password is managed by Cognito, Liferay must not ask for a local one
    user = userLocalService.updatePasswordReset(user.getUserId(), false);
    user = userLocalService.updateExternalReferenceCode(user, identity.sub());
    log.info("Created user " + user.getUserId() + " for Cognito user " + identity.sub());
    return user;
  }

  private User updateUser(long companyId, User user, OidcIdentity identity) {
    var changed = false;
    if (Validator.isNotNull(identity.givenName())
        && !Objects.equals(user.getFirstName(), identity.givenName())) {
      user.setFirstName(identity.givenName());
      changed = true;
    }
    if (Validator.isNotNull(identity.familyName())
        && !Objects.equals(user.getLastName(), identity.familyName())) {
      user.setLastName(identity.familyName());
      changed = true;
    }
    if (!identity.email().equalsIgnoreCase(user.getEmailAddress())) {
      if (!identity.emailVerified()) {
        log.warn("Email of Cognito user " + identity.sub() + " is not verified, not updating it");
      } else if (userLocalService.fetchUserByEmailAddress(companyId, identity.email()) == null) {
        user.setEmailAddress(identity.email());
        changed = true;
      } else {
        log.warn(
            "Email of Cognito user "
                + identity.sub()
                + " is already used by another user, not updating it");
      }
    }
    return changed ? userLocalService.updateUser(user) : user;
  }

  /** Liferay sets the external reference code to the UUID when none is given. */
  private static boolean isUnlinked(User user) {
    return Validator.isNull(user.getExternalReferenceCode())
        || user.getExternalReferenceCode().equals(user.getUuid());
  }

  private static boolean isDefaultAdmin(User user) {
    var adminScreenName = PropsUtil.get(PropsKeys.DEFAULT_ADMIN_SCREEN_NAME);
    return Validator.isNotNull(adminScreenName)
        && adminScreenName.equalsIgnoreCase(user.getScreenName());
  }

  private static String firstName(OidcIdentity identity) {
    return Validator.isNotNull(identity.givenName()) ? identity.givenName() : emailName(identity);
  }

  /** Liferay requires a last name for the fi_FI locale, the claim is optional in Cognito. */
  private static String lastName(OidcIdentity identity) {
    return Validator.isNotNull(identity.familyName()) ? identity.familyName() : emailName(identity);
  }

  private static String emailName(OidcIdentity identity) {
    var at = identity.email().indexOf('@');
    return at > 0 ? identity.email().substring(0, at) : identity.email();
  }
}
