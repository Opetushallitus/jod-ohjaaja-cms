/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.auth.test;

import com.liferay.application.list.PanelApp;
import com.liferay.application.list.PanelAppRegistry;
import com.liferay.application.list.constants.PanelCategoryKeys;
import com.liferay.journal.constants.JournalPortletKeys;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.PermissionCheckerFactory;
import com.liferay.portal.kernel.security.permission.PermissionThreadLocal;
import com.liferay.portal.kernel.security.permission.ResourceActionsUtil;
import com.liferay.portal.kernel.service.PortletLocalServiceUtil;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.service.permission.PortletPermissionUtil;
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

/** Verifies what a content producer can access in the site administration. */
@RunWith(JodInContainerRunner.class)
public class ContentProducerAccessTest {

  @ClassRule @Rule
  public static final LiferayIntegrationTestRule liferayIntegrationTestRule =
      new LiferayIntegrationTestRule();

  private static final String OHJAAJA_PANEL_CATEGORY_KEY =
      "site_administration.fi_okm_jod_ohjaaja_cms_ohjaaja";

  private static final List<String> OHJAAJA_PORTLETS =
      List.of(
          "fi_okm_jod_ohjaaja_cms_studyprogram_portlet_StudyProgramImporterPortlet",
          "fi_okm_jod_ohjaaja_cms_comments_moderation_portlet_CommentsModerationPortlet",
          "fi_okm_jod_ohjaaja_cms_statistics_portlet_StatisticsPortlet");

  private static BundleContext bundleContext;
  private static PermissionChecker originalPermissionChecker;
  private static PermissionCheckerFactory permissionCheckerFactory;
  private static PanelAppRegistry panelAppRegistry;
  private static RoleSynchronizer roleSynchronizer;
  private static UserLocalService userLocalService;
  private static Group group;

  private User user;

  @BeforeClass
  public static void setUpClass() throws Exception {
    bundleContext = FrameworkUtil.getBundle(ContentProducerAccessTest.class).getBundleContext();
    permissionCheckerFactory = getService(PermissionCheckerFactory.class);
    panelAppRegistry = getService(PanelAppRegistry.class);
    roleSynchronizer = getService(RoleSynchronizer.class);
    userLocalService = getService(UserLocalService.class);
    group = getService(JodOhjaajaCmsUtil.class).getJodOhjaajaCmsGroup();

    originalPermissionChecker = PermissionThreadLocal.getPermissionChecker();
    PermissionThreadLocal.setPermissionChecker(
        permissionCheckerFactory.create(TestPropsValues.getUser()));
    getService(ContentProducerRoleInitializer.class).initRole(TestPropsValues.getCompanyId());
  }

  @AfterClass
  public static void tearDownClass() {
    PermissionThreadLocal.setPermissionChecker(originalPermissionChecker);
  }

  @Before
  public void setUp() throws Exception {
    user = UserTestUtil.addUser();
    roleSynchronizer.synchronize(user, List.of("cms-sisallontuottaja"));
  }

  @After
  public void tearDown() throws Exception {
    userLocalService.deleteUser(user.getUserId());
  }

  @Test
  public void testContentProducerHasControlPanelAccessToPortlets() throws Exception {
    var permissionChecker = permissionCheckerFactory.create(user);

    Assert.assertTrue(
        JournalPortletKeys.JOURNAL,
        PortletPermissionUtil.hasControlPanelAccessPermission(
            permissionChecker, group.getGroupId(), JournalPortletKeys.JOURNAL));
    for (var portletId : OHJAAJA_PORTLETS) {
      var portlet = PortletLocalServiceUtil.getPortletById(portletId);
      Assert.assertTrue(
          portletId
              + " category="
              + portlet.getControlPanelEntryCategory()
              + " actions="
              + ResourceActionsUtil.getResourceActions(portletId),
          PortletPermissionUtil.hasControlPanelAccessPermission(
              permissionChecker, group.getGroupId(), portletId));
    }
  }

  @Test
  public void testContentProducerSeesOhjaajaPanelApps() throws Exception {
    var permissionChecker = permissionCheckerFactory.create(user);

    var panelApps =
        panelAppRegistry.getPanelApps(OHJAAJA_PANEL_CATEGORY_KEY, permissionChecker, group);

    Assert.assertEquals(
        "Visible Ohjaaja panel apps: " + portletIds(panelApps),
        OHJAAJA_PORTLETS.size(),
        panelApps.size());
  }

  @Test
  public void testContentProducerSeesWebContent() throws Exception {
    var permissionChecker = permissionCheckerFactory.create(user);

    var panelApps =
        panelAppRegistry.getPanelApps(
            PanelCategoryKeys.SITE_ADMINISTRATION_CONTENT, permissionChecker, group);

    Assert.assertTrue(
        "Visible content panel apps: " + portletIds(panelApps),
        portletIds(panelApps).contains(JournalPortletKeys.JOURNAL));
  }

  private static List<String> portletIds(List<PanelApp> panelApps) {
    return panelApps.stream().map(PanelApp::getPortletId).toList();
  }

  private static <T> T getService(Class<T> clazz) {
    return bundleContext.getService(bundleContext.getServiceReference(clazz));
  }
}
