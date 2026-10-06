/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.auth.test;

import com.liferay.portal.kernel.model.Role;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.model.role.RoleConstants;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.PermissionCheckerFactory;
import com.liferay.portal.kernel.security.permission.PermissionThreadLocal;
import com.liferay.portal.kernel.service.RoleLocalService;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.RoleTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.test.util.UserTestUtil;
import com.liferay.portal.kernel.util.PropsKeys;
import com.liferay.portal.kernel.util.PropsUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import fi.okm.jod.ohjaaja.cms.auth.jwt.OidcIdentity;
import fi.okm.jod.ohjaaja.cms.auth.role.ContentProducerRoleInitializer;
import fi.okm.jod.ohjaaja.cms.auth.user.CognitoUserLogin;
import fi.okm.jod.ohjaaja.cms.auth.user.LoginResult;
import fi.okm.jod.ohjaaja.cms.auth.user.LoginResult.Allowed;
import fi.okm.jod.ohjaaja.cms.auth.user.LoginResult.DenialReason;
import fi.okm.jod.ohjaaja.cms.auth.user.LoginResult.Denied;
import fi.okm.jod.ohjaaja.cms.testrunner.client.JodInContainerRunner;
import fi.okm.jod.ohjaaja.cms.util.JodOhjaajaCmsUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;

/** Integration tests for resolving and linking Liferay users from Cognito identities. */
@RunWith(JodInContainerRunner.class)
public class CognitoUserLoginTest {

  @ClassRule @Rule
  public static final LiferayIntegrationTestRule liferayIntegrationTestRule =
      new LiferayIntegrationTestRule();

  private static final List<String> ADMIN_GROUPS = List.of("cms-admin");

  private static BundleContext bundleContext;
  private static PermissionChecker originalPermissionChecker;
  private static CognitoUserLogin cognitoUserLogin;
  private static UserLocalService userLocalService;
  private static RoleLocalService roleLocalService;
  private static Role administratorRole;
  private static long companyId;
  private static long groupId;

  private final List<Long> userIds = new ArrayList<>();
  private final List<Long> roleIds = new ArrayList<>();

  @BeforeClass
  public static void setUpClass() throws Exception {
    bundleContext = FrameworkUtil.getBundle(CognitoUserLoginTest.class).getBundleContext();
    cognitoUserLogin = getService(CognitoUserLogin.class);
    userLocalService = getService(UserLocalService.class);
    roleLocalService = getService(RoleLocalService.class);
    groupId = getService(JodOhjaajaCmsUtil.class).getJodOhjaajaCmsGroup().getGroupId();

    originalPermissionChecker = PermissionThreadLocal.getPermissionChecker();
    PermissionThreadLocal.setPermissionChecker(
        getService(PermissionCheckerFactory.class).create(TestPropsValues.getUser()));

    companyId = TestPropsValues.getCompanyId();
    administratorRole = roleLocalService.getRole(companyId, RoleConstants.ADMINISTRATOR);
    getService(ContentProducerRoleInitializer.class).initRole(companyId);
  }

  @AfterClass
  public static void tearDownClass() {
    PermissionThreadLocal.setPermissionChecker(originalPermissionChecker);
  }

  @After
  public void tearDown() throws Exception {
    for (var userId : userIds) {
      if (userLocalService.fetchUser(userId) != null) {
        userLocalService.deleteUser(userId);
      }
    }
    for (var roleId : roleIds) {
      roleLocalService.deleteRole(roleId);
    }
  }

  @Test
  public void testNewUserIsCreatedAndLinked() throws Exception {
    var identity = identity(randomEmail(), ADMIN_GROUPS);

    var user = loginAllowed(identity);

    Assert.assertEquals(identity.sub(), user.getExternalReferenceCode());
    Assert.assertEquals(identity.email(), user.getEmailAddress());
    Assert.assertEquals("Matti", user.getFirstName());
    Assert.assertEquals("Meikäläinen", user.getLastName());
    Assert.assertFalse(userLocalService.getUser(user.getUserId()).isPasswordReset());
    Assert.assertTrue(userLocalService.hasGroupUser(groupId, user.getUserId()));
    Assert.assertTrue(hasAdministratorRole(user));
  }

  @Test
  public void testKnownUserIsFoundBySubjectAndUpdated() throws Exception {
    var identity = identity(randomEmail(), ADMIN_GROUPS);
    var user = loginAllowed(identity);

    var changed =
        new OidcIdentity(identity.sub(), randomEmail(), true, "Maija", "Mallikas", ADMIN_GROUPS);
    var updated = loginAllowed(changed);

    Assert.assertEquals(user.getUserId(), updated.getUserId());
    Assert.assertEquals(changed.email(), updated.getEmailAddress());
    Assert.assertEquals("Maija", updated.getFirstName());
    Assert.assertEquals("Mallikas", updated.getLastName());
  }

  @Test
  public void testNewUserWithoutNamesIsCreated() throws Exception {
    var email = randomEmail();
    var identity =
        new OidcIdentity(UUID.randomUUID().toString(), email, true, null, null, ADMIN_GROUPS);

    var user = loginAllowed(identity);

    var emailName = email.substring(0, email.indexOf('@'));
    Assert.assertEquals(emailName, user.getFirstName());
    Assert.assertEquals(emailName, user.getLastName());
  }

  @Test
  public void testMissingNamesDoNotOverwriteExistingNames() throws Exception {
    var identity = identity(randomEmail(), ADMIN_GROUPS);
    loginAllowed(identity);

    var withoutNames =
        new OidcIdentity(identity.sub(), identity.email(), true, null, null, ADMIN_GROUPS);
    var user = loginAllowed(withoutNames);

    Assert.assertEquals("Matti", user.getFirstName());
    Assert.assertEquals("Meikäläinen", user.getLastName());
  }

  @Test
  public void testUnverifiedEmailIsNotUpdated() throws Exception {
    var identity = identity(randomEmail(), ADMIN_GROUPS);
    loginAllowed(identity);

    var unverified =
        new OidcIdentity(
            identity.sub(), randomEmail(), false, "Matti", "Meikäläinen", ADMIN_GROUPS);
    var user = loginAllowed(unverified);

    Assert.assertEquals(identity.email(), user.getEmailAddress());
  }

  @Test
  public void testUnlinkedUserIsLinkedByEmail() throws Exception {
    var existing = addUnlinkedUser();
    var identity = identity(existing.getEmailAddress(), ADMIN_GROUPS);

    var user = loginAllowed(identity);

    Assert.assertEquals(existing.getUserId(), user.getUserId());
    Assert.assertEquals(identity.sub(), user.getExternalReferenceCode());
    Assert.assertTrue(hasAdministratorRole(user));
  }

  @Test
  public void testUnlinkedUserIsNotLinkedByUnverifiedEmail() throws Exception {
    var existing = addUnlinkedUser();
    var identity =
        new OidcIdentity(
            UUID.randomUUID().toString(),
            existing.getEmailAddress(),
            false,
            "Matti",
            "Meikäläinen",
            ADMIN_GROUPS);

    assertDenied(DenialReason.ACCOUNT_CONFLICT, login(identity));

    var user = userLocalService.getUser(existing.getUserId());
    Assert.assertEquals(user.getUuid(), user.getExternalReferenceCode());
    Assert.assertFalse(hasAdministratorRole(user));
    Assert.assertNull(userLocalService.fetchUserByExternalReferenceCode(identity.sub(), companyId));
  }

  @Test
  public void testUserLinkedToOtherSubjectIsNotRelinked() throws Exception {
    var existing = addUnlinkedUser();
    var first = identity(existing.getEmailAddress(), ADMIN_GROUPS);
    loginAllowed(first);

    var second = identity(existing.getEmailAddress(), ADMIN_GROUPS);

    assertDenied(DenialReason.ACCOUNT_CONFLICT, login(second));
    Assert.assertEquals(
        first.sub(), userLocalService.getUser(existing.getUserId()).getExternalReferenceCode());
    Assert.assertNull(userLocalService.fetchUserByExternalReferenceCode(second.sub(), companyId));
  }

  @Test
  public void testMappedRolesAreRevokedWhenLastGroupIsRemoved() throws Exception {
    var identity = identity(randomEmail(), ADMIN_GROUPS);
    var user = loginAllowed(identity);
    var unmappedRole = addUnmappedRole(user);
    Assert.assertTrue(hasAdministratorRole(user));

    var withoutGroups =
        new OidcIdentity(
            identity.sub(),
            identity.email(),
            identity.emailVerified(),
            identity.givenName(),
            identity.familyName(),
            List.of("unknown-group"));

    assertDenied(DenialReason.NO_ACCESS, login(withoutGroups));
    Assert.assertFalse(hasAdministratorRole(user));
    Assert.assertTrue(userLocalService.hasRoleUser(unmappedRole.getRoleId(), user.getUserId()));
  }

  @Test
  public void testUnlinkedUserIsNotTouchedWithoutGroups() throws Exception {
    var existing = addUnlinkedUser();
    userLocalService.addRoleUser(administratorRole.getRoleId(), existing.getUserId());

    assertDenied(DenialReason.NO_ACCESS, login(identity(existing.getEmailAddress(), List.of())));

    var user = userLocalService.getUser(existing.getUserId());
    Assert.assertEquals(user.getUuid(), user.getExternalReferenceCode());
    Assert.assertTrue(hasAdministratorRole(user));
  }

  @Test
  public void testDeactivatedUserIsNotLoggedIn() throws Exception {
    var identity = identity(randomEmail(), ADMIN_GROUPS);
    var user = loginAllowed(identity);
    userLocalService.updateStatus(
        user.getUserId(), WorkflowConstants.STATUS_INACTIVE, new ServiceContext());

    assertDenied(DenialReason.ACCOUNT_CONFLICT, login(identity));
  }

  @Test
  public void testDefaultAdminIsNotLoggedIn() throws Exception {
    var defaultAdmin =
        userLocalService.fetchUserByScreenName(
            companyId, PropsUtil.get(PropsKeys.DEFAULT_ADMIN_SCREEN_NAME));
    Assume.assumeNotNull(defaultAdmin);
    var externalReferenceCode = defaultAdmin.getExternalReferenceCode();

    assertDenied(
        DenialReason.ACCOUNT_CONFLICT,
        login(identity(defaultAdmin.getEmailAddress(), ADMIN_GROUPS)));
    Assert.assertEquals(
        externalReferenceCode,
        userLocalService.getUser(defaultAdmin.getUserId()).getExternalReferenceCode());
  }

  private LoginResult login(OidcIdentity identity) throws Exception {
    var result = cognitoUserLogin.login(companyId, identity);
    if (result instanceof Allowed(var user)) {
      userIds.add(user.getUserId());
    }
    return result;
  }

  private User loginAllowed(OidcIdentity identity) throws Exception {
    if (login(identity) instanceof Allowed(var user)) {
      return user;
    }
    throw new AssertionError("Cognito user " + identity.sub() + " was not logged in");
  }

  private static void assertDenied(DenialReason reason, LoginResult result) {
    Assert.assertEquals(new Denied(reason), result);
  }

  private User addUnlinkedUser() throws Exception {
    var user = UserTestUtil.addUser();
    userIds.add(user.getUserId());
    Assert.assertEquals(user.getUuid(), user.getExternalReferenceCode());
    return user;
  }

  private Role addUnmappedRole(User user) throws Exception {
    var role = RoleTestUtil.addRole(RoleConstants.TYPE_REGULAR);
    roleIds.add(role.getRoleId());
    userLocalService.addRoleUser(role.getRoleId(), user.getUserId());
    return role;
  }

  private static boolean hasAdministratorRole(User user) {
    return userLocalService.hasRoleUser(administratorRole.getRoleId(), user.getUserId());
  }

  private static OidcIdentity identity(String email, List<String> groups) {
    return new OidcIdentity(
        UUID.randomUUID().toString(), email, true, "Matti", "Meikäläinen", groups);
  }

  private static String randomEmail() {
    return StringUtil.toLowerCase(RandomTestUtil.randomString()) + "@example.com";
  }

  private static <T> T getService(Class<T> clazz) {
    return bundleContext.getService(bundleContext.getServiceReference(clazz));
  }
}
