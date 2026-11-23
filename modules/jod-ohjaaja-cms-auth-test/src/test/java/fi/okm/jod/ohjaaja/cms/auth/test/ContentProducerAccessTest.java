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
import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.data.engine.rest.resource.v2_0.DataDefinitionResource;
import com.liferay.headless.delivery.dto.v1_0.StructuredContent;
import com.liferay.headless.delivery.resource.v1_0.StructuredContentResource;
import com.liferay.journal.constants.JournalPortletKeys;
import com.liferay.journal.exception.InvalidDDMStructureException;
import com.liferay.journal.service.JournalArticleLocalService;
import com.liferay.journal.service.JournalFolderLocalService;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
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
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.portal.test.log.LoggerTestUtil;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import fi.okm.jod.ohjaaja.cms.auth.role.ContentProducerRoleInitializer;
import fi.okm.jod.ohjaaja.cms.auth.role.RoleSynchronizer;
import fi.okm.jod.ohjaaja.cms.util.JodOhjaajaCmsUtil;
import fi.okm.jod.ohjaaja.cms.util.StructureInitializer;
import java.util.ArrayList;
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

/** Verifies what a content producer can access in the site administration. */
@RunWith(Arquillian.class)
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

  @Inject private static PermissionCheckerFactory permissionCheckerFactory;
  @Inject private static PanelAppRegistry panelAppRegistry;
  @Inject private static RoleSynchronizer roleSynchronizer;
  @Inject private static UserLocalService userLocalService;
  @Inject private static JodOhjaajaCmsUtil jodOhjaajaCmsUtil;
  @Inject private static ContentProducerRoleInitializer contentProducerRoleInitializer;
  @Inject private static StructureInitializer structureInitializer;
  @Inject private static DataDefinitionResource.Factory dataDefinitionResourceFactory;
  @Inject private static StructuredContentResource.Factory structuredContentResourceFactory;
  @Inject private static JournalArticleLocalService journalArticleLocalService;
  @Inject private static JournalFolderLocalService journalFolderLocalService;

  private static PermissionChecker originalPermissionChecker;
  private static Group group;

  private User user;
  private final List<Long> structuredContentIds = new ArrayList<>();

  @BeforeClass
  public static void setUpClass() throws Exception {
    group = jodOhjaajaCmsUtil.getJodOhjaajaCmsGroup();

    originalPermissionChecker = PermissionThreadLocal.getPermissionChecker();
    PermissionThreadLocal.setPermissionChecker(
        permissionCheckerFactory.create(TestPropsValues.getUser()));
    contentProducerRoleInitializer.initRole(TestPropsValues.getCompanyId());

    // Liferay sets a portlet's control panel entry category only when the panel apps of that
    // category are first looked up (normally by rendering the product menu). Without this, the
    // control panel access check fails on a freshly started Liferay unless a panel app test
    // happened to run first.
    var adminPermissionChecker = PermissionThreadLocal.getPermissionChecker();
    panelAppRegistry.getPanelApps(
        PanelCategoryKeys.SITE_ADMINISTRATION_CONTENT, adminPermissionChecker, group);
    panelAppRegistry.getPanelApps(OHJAAJA_PANEL_CATEGORY_KEY, adminPermissionChecker, group);
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
    var structuredContentResource =
        structuredContentResourceFactory
            .create()
            .user(TestPropsValues.getUser())
            .checkPermissions(false)
            .build();
    try {
      for (var structuredContentId : structuredContentIds) {
        structuredContentResource.deleteStructuredContent(structuredContentId);
      }
    } finally {
      userLocalService.deleteUser(user.getUserId());
    }
  }

  @Test
  public void testContentProducerCanAddAndUpdateNews() throws Exception {
    // The initializer only logs its failures, which would otherwise surface below as a missing
    // structure or folder
    try (var logCapture =
        LoggerTestUtil.configureLog4JLogger(
            "fi.okm.jod.ohjaaja.cms.util.StructureInitializerImpl", LoggerTestUtil.ERROR)) {
      structureInitializer.initStructures();

      Assert.assertEquals(List.of(), logCapture.getMessages());
    }
    var newsStructureId =
        getStructureId(StructureInitializer.NEWS_STRUCTURE_EXTERNAL_REFERENCE_CODE);
    var newsFolder =
        journalFolderLocalService.fetchJournalFolderByExternalReferenceCode(
            StructureInitializer.NEWS_FOLDER_EXTERNAL_REFERENCE_CODE, group.getGroupId());
    Assert.assertNotNull("News folder not found", newsFolder);
    var structuredContentResource =
        structuredContentResourceFactory
            .create()
            .user(user)
            .preferredLocale(LocaleUtil.US)
            .checkPermissions(true)
            .build();

    var added =
        structuredContentResource.postStructuredContentFolderStructuredContent(
            newsFolder.getFolderId(), news(newsStructureId, "Uutinen"));
    structuredContentIds.add(added.getId());
    var article = journalArticleLocalService.getLatestArticle(added.getId());
    Assert.assertEquals(newsFolder.getFolderId(), article.getFolderId());
    Assert.assertEquals(WorkflowConstants.STATUS_APPROVED, article.getStatus());
    // The public site reads the news as a guest, with permission checks
    Assert.assertEquals(
        "Uutinen",
        structuredContentResourceFactory
            .create()
            .user(userLocalService.getGuestUser(TestPropsValues.getCompanyId()))
            .preferredLocale(LocaleUtil.US)
            .checkPermissions(true)
            .build()
            .getStructuredContent(added.getId())
            .getTitle());

    // Only news can be added to the news folder
    try {
      var addedArticle =
          structuredContentResource.postStructuredContentFolderStructuredContent(
              newsFolder.getFolderId(),
              news(
                  getStructureId(StructureInitializer.ARTICLE_STRUCTURE_EXTERNAL_REFERENCE_CODE),
                  "Artikkeli"));
      structuredContentIds.add(addedArticle.getId());
      Assert.fail("The content producer added an article to the news folder");
    } catch (InvalidDDMStructureException e) {
      // Expected
    }

    // Added by someone else, so that the user is not its owner
    var othersNews =
        structuredContentResourceFactory
            .create()
            .user(TestPropsValues.getUser())
            .preferredLocale(LocaleUtil.US)
            .checkPermissions(false)
            .build()
            .postStructuredContentFolderStructuredContent(
                newsFolder.getFolderId(), news(newsStructureId, "Toisen uutinen"));
    structuredContentIds.add(othersNews.getId());
    var updated =
        structuredContentResource.putStructuredContent(
            othersNews.getId(), news(newsStructureId, "Päivitetty uutinen"));

    Assert.assertEquals("Päivitetty uutinen", updated.getTitle());
    Assert.assertEquals(
        WorkflowConstants.STATUS_APPROVED,
        journalArticleLocalService.getLatestArticle(updated.getId()).getStatus());
  }

  private static long getStructureId(String externalReferenceCode) throws Exception {
    return dataDefinitionResourceFactory
        .create()
        .user(TestPropsValues.getUser())
        .checkPermissions(false)
        .build()
        .getSiteDataDefinitionByContentTypeByExternalReferenceCode(
            group.getGroupId(), "journal", externalReferenceCode)
        .getId();
  }

  private static StructuredContent news(long structureId, String title) {
    var ingress =
        JSONFactoryUtil.createJSONObject()
            .put("name", "ingress")
            .put("fieldReference", "ingress")
            .put("contentFieldValue", JSONFactoryUtil.createJSONObject().put("data", "Ingressi"));
    var content =
        JSONFactoryUtil.createJSONObject()
            .put("name", "content")
            .put("fieldReference", "content")
            .put(
                "contentFieldValue",
                JSONFactoryUtil.createJSONObject().put("data", "<p>Teksti</p>"));
    return StructuredContent.toDTO(
        JSONFactoryUtil.createJSONObject()
            .put("contentStructureId", structureId)
            .put("title", title)
            .put("viewableBy", "Anyone")
            .put("contentFields", JSONFactoryUtil.createJSONArray().put(ingress).put(content))
            .toString());
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
}
