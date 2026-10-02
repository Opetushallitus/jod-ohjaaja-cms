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
import com.liferay.portal.kernel.service.UserGroupRoleLocalService;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.test.util.RoleTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.test.util.UserTestUtil;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import fi.okm.jod.ohjaaja.cms.auth.role.ContentProducerRoleInitializer;
import fi.okm.jod.ohjaaja.cms.auth.role.RoleSynchronizer;
import fi.okm.jod.ohjaaja.cms.testrunner.client.JodInContainerRunner;
import fi.okm.jod.ohjaaja.cms.util.JodOhjaajaCmsUtil;
import java.util.List;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;

/** Integration tests for synchronizing Liferay roles from Cognito groups. */
@RunWith(JodInContainerRunner.class)
public class RoleSynchronizerTest {

  @ClassRule @Rule
  public static final LiferayIntegrationTestRule liferayIntegrationTestRule =
      new LiferayIntegrationTestRule();

  private static BundleContext bundleContext;
  private static PermissionChecker originalPermissionChecker;
  private static RoleSynchronizer roleSynchronizer;
  private static RoleLocalService roleLocalService;
  private static UserLocalService userLocalService;
  private static UserGroupRoleLocalService userGroupRoleLocalService;
  private static Role administratorRole;
  private static Role contentProducerRole;
  private static long groupId;

  private User user;
  private Role unmappedRole;

  @BeforeClass
  public static void setUpClass() throws Exception {
    bundleContext = FrameworkUtil.getBundle(RoleSynchronizerTest.class).getBundleContext();
    roleSynchronizer = getService(RoleSynchronizer.class);
    roleLocalService = getService(RoleLocalService.class);
    userLocalService = getService(UserLocalService.class);
    userGroupRoleLocalService = getService(UserGroupRoleLocalService.class);
    groupId = getService(JodOhjaajaCmsUtil.class).getJodOhjaajaCmsGroup().getGroupId();

    originalPermissionChecker = PermissionThreadLocal.getPermissionChecker();
    PermissionThreadLocal.setPermissionChecker(
        getService(PermissionCheckerFactory.class).create(TestPropsValues.getUser()));

    var companyId = TestPropsValues.getCompanyId();
    administratorRole = roleLocalService.getRole(companyId, RoleConstants.ADMINISTRATOR);
    contentProducerRole = getService(ContentProducerRoleInitializer.class).initRole(companyId);
  }

  @AfterClass
  public static void tearDownClass() {
    PermissionThreadLocal.setPermissionChecker(originalPermissionChecker);
  }

  @Before
  public void setUp() throws Exception {
    user = UserTestUtil.addUser();
    unmappedRole = RoleTestUtil.addRole(RoleConstants.TYPE_REGULAR);
    userLocalService.addRoleUser(unmappedRole.getRoleId(), user.getUserId());
  }

  @After
  public void tearDown() throws Exception {
    userLocalService.deleteUser(user.getUserId());
    roleLocalService.deleteRole(unmappedRole.getRoleId());
  }

  @Test
  public void testContentProducerSiteRoleIsSynchronized() throws Exception {
    roleSynchronizer.synchronize(user, List.of("cms-sisallontuottaja"));
    Assert.assertTrue(hasContentProducerRole());
    Assert.assertFalse(hasAdministratorRole());

    roleSynchronizer.synchronize(user, List.of());
    Assert.assertFalse(hasContentProducerRole());
  }

  @Test
  public void testAdministratorRoleIsSynchronized() throws Exception {
    roleSynchronizer.synchronize(user, List.of("cms-admin", "unknown-group"));
    Assert.assertTrue(hasAdministratorRole());
    Assert.assertFalse(hasContentProducerRole());

    roleSynchronizer.synchronize(user, List.of("unknown-group"));
    Assert.assertFalse(hasAdministratorRole());
  }

  @Test
  public void testUnmappedRolesAreKept() throws Exception {
    roleSynchronizer.synchronize(user, List.of("cms-sisallontuottaja"));
    roleSynchronizer.synchronize(user, List.of());

    Assert.assertTrue(userLocalService.hasRoleUser(unmappedRole.getRoleId(), user.getUserId()));
  }

  private boolean hasAdministratorRole() {
    return userLocalService.hasRoleUser(administratorRole.getRoleId(), user.getUserId());
  }

  private boolean hasContentProducerRole() {
    return userGroupRoleLocalService.hasUserGroupRole(
        user.getUserId(), groupId, contentProducerRole.getRoleId());
  }

  private static <T> T getService(Class<T> clazz) {
    return bundleContext.getService(bundleContext.getServiceReference(clazz));
  }
}
