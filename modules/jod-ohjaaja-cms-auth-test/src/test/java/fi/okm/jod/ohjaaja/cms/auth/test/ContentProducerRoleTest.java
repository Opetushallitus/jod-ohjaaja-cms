/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.auth.test;

import com.liferay.journal.model.JournalArticle;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.ResourceConstants;
import com.liferay.portal.kernel.model.Role;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.model.role.RoleConstants;
import com.liferay.portal.kernel.security.permission.ActionKeys;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.PermissionCheckerFactory;
import com.liferay.portal.kernel.security.permission.PermissionThreadLocal;
import com.liferay.portal.kernel.service.ResourcePermissionLocalService;
import com.liferay.portal.kernel.service.RoleLocalService;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.test.rule.DataGuard;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import fi.okm.jod.ohjaaja.cms.auth.role.ContentProducerRole;
import fi.okm.jod.ohjaaja.cms.auth.role.ContentProducerRoleInitializer;
import fi.okm.jod.ohjaaja.cms.testrunner.client.JodInContainerRunner;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;

/**
 * Integration tests for the content producer role created by ContentProducerRoleInitializer.
 *
 * <p>The role and its permissions persist by design, so the data guard is disabled.
 */
@DataGuard(scope = DataGuard.Scope.NONE)
@RunWith(JodInContainerRunner.class)
public class ContentProducerRoleTest {

  @ClassRule @Rule
  public static final LiferayIntegrationTestRule liferayIntegrationTestRule =
      new LiferayIntegrationTestRule();

  private static final String OHJAAJA_PORTLET =
      "fi_okm_jod_ohjaaja_cms_statistics_portlet_StatisticsPortlet";

  private static BundleContext bundleContext;
  private static PermissionChecker originalPermissionChecker;
  private static ContentProducerRoleInitializer initializer;
  private static RoleLocalService roleLocalService;
  private static ResourcePermissionLocalService resourcePermissionLocalService;
  private static long companyId;

  @BeforeClass
  public static void setUpClass() throws Exception {
    bundleContext = FrameworkUtil.getBundle(ContentProducerRoleTest.class).getBundleContext();
    initializer = getService(ContentProducerRoleInitializer.class);
    roleLocalService = getService(RoleLocalService.class);
    resourcePermissionLocalService = getService(ResourcePermissionLocalService.class);
    companyId = TestPropsValues.getCompanyId();

    originalPermissionChecker = PermissionThreadLocal.getPermissionChecker();
    User adminUser = TestPropsValues.getUser();
    PermissionThreadLocal.setPermissionChecker(
        getService(PermissionCheckerFactory.class).create(adminUser));
  }

  @AfterClass
  public static void tearDownClass() {
    PermissionThreadLocal.setPermissionChecker(originalPermissionChecker);
  }

  @Test
  public void testRoleIsCreatedAsSiteRole() throws Exception {
    var role = initializer.initRole(companyId);

    Assert.assertEquals(ContentProducerRole.NAME, role.getName());
    Assert.assertEquals(
        ContentProducerRole.EXTERNAL_REFERENCE_CODE, role.getExternalReferenceCode());
    Assert.assertEquals(RoleConstants.TYPE_SITE, role.getType());
    Assert.assertEquals(
        ContentProducerRole.TITLE_FI, role.getTitle(LocaleUtil.fromLanguageId("fi_FI")));
    Assert.assertEquals(ContentProducerRole.TITLE_EN, role.getTitle(LocaleUtil.US));
    Assert.assertEquals(ContentProducerRole.DESCRIPTION_FI, role.getDescription("fi_FI"));
  }

  @Test
  public void testRoleResourcesExist() throws Exception {
    var role = initializer.initRole(companyId);
    Assert.assertTrue(getRoleResourcePermissionsCount(role) > 0);

    for (var resourcePermission :
        resourcePermissionLocalService.getResourcePermissions(
            companyId,
            Role.class.getName(),
            ResourceConstants.SCOPE_INDIVIDUAL,
            String.valueOf(role.getRoleId()))) {
      resourcePermissionLocalService.deleteResourcePermission(resourcePermission);
    }
    Assert.assertEquals(0, getRoleResourcePermissionsCount(role));

    initializer.initRole(companyId);

    Assert.assertTrue(getRoleResourcePermissionsCount(role) > 0);
  }

  @Test
  public void testInitIsIdempotent() throws Exception {
    var first = initializer.initRole(companyId);
    var second = initializer.initRole(companyId);

    Assert.assertEquals(first.getRoleId(), second.getRoleId());
    Assert.assertNull(roleLocalService.fetchRole(companyId, ContentProducerRole.LEGACY_NAME));
  }

  @Test
  public void testPermissionsAreGranted() throws Exception {
    var role = initializer.initRole(companyId);

    Assert.assertTrue(hasPermission(role, Group.class.getName(), "VIEW_SITE_ADMINISTRATION"));
    Assert.assertTrue(hasPermission(role, JournalArticle.class.getName(), ActionKeys.UPDATE));
    Assert.assertTrue(hasPermission(role, "com.liferay.journal", ActionKeys.ADD_ARTICLE));
    Assert.assertTrue(hasPermission(role, OHJAAJA_PORTLET, ActionKeys.ACCESS_IN_CONTROL_PANEL));
  }

  @Test
  public void testUnmanagedPermissionsAreRemoved() throws Exception {
    var role = initializer.initRole(companyId);
    resourcePermissionLocalService.addResourcePermission(
        companyId,
        JournalArticle.class.getName(),
        ResourceConstants.SCOPE_GROUP_TEMPLATE,
        "0",
        role.getRoleId(),
        ActionKeys.PERMISSIONS);
    resourcePermissionLocalService.addResourcePermission(
        companyId,
        User.class.getName(),
        ResourceConstants.SCOPE_GROUP_TEMPLATE,
        "0",
        role.getRoleId(),
        ActionKeys.VIEW);
    Assert.assertTrue(hasPermission(role, JournalArticle.class.getName(), ActionKeys.PERMISSIONS));

    initializer.initRole(companyId);

    Assert.assertFalse(hasPermission(role, JournalArticle.class.getName(), ActionKeys.PERMISSIONS));
    Assert.assertFalse(hasPermission(role, User.class.getName(), ActionKeys.VIEW));
    Assert.assertTrue(hasPermission(role, JournalArticle.class.getName(), ActionKeys.UPDATE));
  }

  @Test
  public void testLegacyRoleIsTakenOver() throws Exception {
    var role = initializer.initRole(companyId);
    var serviceContext = new ServiceContext();
    serviceContext.setCompanyId(companyId);
    roleLocalService.updateRole(
        "legacy-" + role.getRoleId(),
        role.getRoleId(),
        ContentProducerRole.LEGACY_NAME,
        Map.of(LocaleUtil.US, ContentProducerRole.LEGACY_NAME),
        Map.of(),
        role.getSubtype(),
        serviceContext);

    var takenOver = initializer.initRole(companyId);

    Assert.assertEquals(role.getRoleId(), takenOver.getRoleId());
    Assert.assertEquals(ContentProducerRole.NAME, takenOver.getName());
    Assert.assertEquals(
        ContentProducerRole.EXTERNAL_REFERENCE_CODE, takenOver.getExternalReferenceCode());
  }

  private static boolean hasPermission(Role role, String name, String actionId) throws Exception {
    return resourcePermissionLocalService.hasResourcePermission(
        companyId, name, ResourceConstants.SCOPE_GROUP_TEMPLATE, "0", role.getRoleId(), actionId);
  }

  private static int getRoleResourcePermissionsCount(Role role) {
    return resourcePermissionLocalService.getResourcePermissionsCount(
        companyId,
        Role.class.getName(),
        ResourceConstants.SCOPE_INDIVIDUAL,
        String.valueOf(role.getRoleId()));
  }

  private static <T> T getService(Class<T> clazz) {
    return bundleContext.getService(bundleContext.getServiceReference(clazz));
  }
}
