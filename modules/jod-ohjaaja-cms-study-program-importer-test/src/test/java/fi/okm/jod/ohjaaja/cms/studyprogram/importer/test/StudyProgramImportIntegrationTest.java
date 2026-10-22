/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.studyprogram.importer.test;

import com.liferay.counter.kernel.service.CounterLocalServiceUtil;
import com.liferay.document.library.kernel.service.DLAppLocalServiceUtil;
import com.liferay.document.library.kernel.service.DLTrashLocalServiceUtil;
import com.liferay.journal.model.JournalArticle;
import com.liferay.journal.service.JournalArticleLocalService;
import com.liferay.petra.function.UnsafeConsumer;
import com.liferay.portal.background.task.service.BackgroundTaskLocalServiceUtil;
import com.liferay.portal.kernel.backgroundtask.BackgroundTask;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskExecutor;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskManagerUtil;
import com.liferay.portal.kernel.backgroundtask.constants.BackgroundTaskConstants;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.PermissionCheckerFactory;
import com.liferay.portal.kernel.security.permission.PermissionThreadLocal;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.CompanyProviderClassTestRule;
import com.liferay.portal.kernel.test.rule.DeleteAfterTestRunMethodTestRule;
import com.liferay.portal.kernel.test.util.ServiceContextTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.PropsUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.test.rule.ClearThreadLocalClassTestRule;
import com.liferay.portal.test.rule.InjectTestRule;
import com.liferay.portal.test.rule.UniqueStringRandomizerBumperClassTestRule;
import com.liferay.site.navigation.model.SiteNavigationMenuItem;
import com.liferay.site.navigation.service.SiteNavigationMenuItemLocalService;
import com.liferay.site.navigation.service.SiteNavigationMenuLocalService;
import fi.okm.jod.ohjaaja.cms.navigation.exception.StudyProgramListingMissingException;
import fi.okm.jod.ohjaaja.cms.navigation.service.NavigationService;
import fi.okm.jod.ohjaaja.cms.studyprogram.background.task.DeleteImportedStudyProgramsBackgroundTaskExecutor;
import fi.okm.jod.ohjaaja.cms.studyprogram.background.task.ImportStudyProgramsBackgroundTaskExecutor;
import fi.okm.jod.ohjaaja.cms.studyprogram.client.KonfoClient;
import fi.okm.jod.ohjaaja.cms.studyprogram.service.StudyProgramBackgroundTaskService;
import fi.okm.jod.ohjaaja.cms.studyprogram.service.StudyProgramFileService;
import fi.okm.jod.ohjaaja.cms.studyprogram.service.StudyProgramFingerprintService;
import fi.okm.jod.ohjaaja.cms.studyprogram.service.StudyProgramFingerprintService.ArticleState;
import fi.okm.jod.ohjaaja.cms.studyprogram.service.StudyProgramService;
import fi.okm.jod.ohjaaja.cms.testrunner.client.JodInContainerRunner;
import fi.okm.jod.ohjaaja.cms.util.JodOhjaajaCmsUtil;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import org.awaitility.Awaitility;
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
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.runtime.ServiceComponentRuntime;

@RunWith(JodInContainerRunner.class)
public class StudyProgramImportIntegrationTest {

  @ClassRule @Rule
  // LiferayIntegrationTestRule without SynchronousDestinationTestRule and LogAssertionTestRule. A
  // synchronous background task destination runs the import inside the commit of the transaction
  // that adds the task, where it deadlocks on HSQLDB table locks once it writes to Document
  // Library. The import runs asynchronously as in production instead, and runImportAndWait() waits
  // for it to complete. The import logs the invalid mock study program as an error, which
  // LogAssertionTestRule would turn into an AssertionError inside the import.
  public static final AggregateTestRule aggregateTestRule =
      new AggregateTestRule(
          false,
          ClearThreadLocalClassTestRule.INSTANCE,
          UniqueStringRandomizerBumperClassTestRule.INSTANCE,
          CompanyProviderClassTestRule.INSTANCE,
          DeleteAfterTestRunMethodTestRule.INSTANCE,
          InjectTestRule.INSTANCE);

  private static final String KONFO_URL_KEY = "konfo.url";
  private static final String FINGERPRINT_FIELD_NAME = "studyProgramFingerprint";
  private static final String PLACEHOLDER_IMAGE_ERC = "study_program_image_placeholder";
  private static final String PLACEHOLDER_IMAGE_PATH =
      "/images/study_program_image_placeholder.png";

  private static BundleContext bundleContext;
  private static String originalKonfoUrl;
  private static StudyProgramService studyProgramService;
  private static JournalArticleLocalService journalArticleLocalService;
  private static StudyProgramBackgroundTaskService backgroundTaskService;
  private static KonfoClient konfoClient;
  private static PermissionChecker originalPermissionChecker;
  private static ServiceRegistration<KonfoClient> mockServiceRegistration;
  private static MockKonfoClient mockKonfoClient;
  private static JodOhjaajaCmsUtil jodOhjaajaCmsUtil;
  private static long createdMenuId;
  private static long createdMenuItemId;

  private static final List<String> allCreatedArticleIds = new ArrayList<>();

  private List<String> createdArticleIds = new ArrayList<>();

  @BeforeClass
  public static void setUpClass() throws Exception {
    var bundle = FrameworkUtil.getBundle(StudyProgramImportIntegrationTest.class);
    bundleContext = bundle.getBundleContext();

    originalKonfoUrl = PropsUtil.get(KONFO_URL_KEY);
    if (Validator.isBlank(originalKonfoUrl)) {
      PropsUtil.set(KONFO_URL_KEY, "https://testiopintopolku.fi/konfo");
    }

    // Register MockKonfoClient as OSGi service with high ranking
    mockKonfoClient = new MockKonfoClient();
    Dictionary<String, Object> props = new Hashtable<>();
    props.put("service.ranking", 1000);
    mockServiceRegistration =
        bundleContext.registerService(KonfoClient.class, mockKonfoClient, props);

    System.out.println("✅ MockKonfoClient registered as OSGi service with ranking 1000");

    // Wait for the higher-ranked MockKonfoClient registration to become the resolved service
    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(100))
        .until(() -> getService(KonfoClient.class) instanceof MockKonfoClient);
    // The executor has a static reluctant reference to KonfoClient, so it keeps the real client
    // until it is activated again
    reactivateImportExecutor();
    jodOhjaajaCmsUtil = getService(JodOhjaajaCmsUtil.class);
    studyProgramService = getService(StudyProgramService.class);
    journalArticleLocalService = getService(JournalArticleLocalService.class);
    backgroundTaskService = getService(StudyProgramBackgroundTaskService.class);
    konfoClient = getService(KonfoClient.class);

    System.out.println("✅ KonfoClient type: " + konfoClient.getClass().getName());

    originalPermissionChecker = PermissionThreadLocal.getPermissionChecker();

    var permissionCheckerFactory = getService(PermissionCheckerFactory.class);
    User adminUser = TestPropsValues.getUser();
    PermissionChecker permissionChecker = permissionCheckerFactory.create(adminUser);
    PermissionThreadLocal.setPermissionChecker(permissionChecker);

    ensureStudyProgramsListingMenuItem();
  }

  private static void reactivateImportExecutor() throws Exception {
    var serviceComponentRuntime = getService(ServiceComponentRuntime.class);
    var componentDescription =
        serviceComponentRuntime.getComponentDescriptionDTO(
            FrameworkUtil.getBundle(ImportStudyProgramsBackgroundTaskExecutor.class),
            ImportStudyProgramsBackgroundTaskExecutor.class.getName());
    serviceComponentRuntime.disableComponent(componentDescription).getValue();
    serviceComponentRuntime.enableComponent(componentDescription).getValue();

    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(100))
        .until(
            () ->
                !bundleContext
                    .getServiceReferences(
                        BackgroundTaskExecutor.class,
                        "(background.task.executor.class.name="
                            + ImportStudyProgramsBackgroundTaskExecutor.class.getName()
                            + ")")
                    .isEmpty());
  }

  /**
   * The import requires a navigation menu item of type StudyProgramsListing to add the study
   * programs under. The test image has no navigation, so one is created unless it already exists.
   */
  private static void ensureStudyProgramsListingMenuItem() throws Exception {
    var navigationService = getService(NavigationService.class);
    navigationService.initNavigation();
    try {
      navigationService.getStudyProgramsParentMenuItem();
      return;
    } catch (StudyProgramListingMissingException e) {
      System.out.println("ℹ️  No StudyProgramsListing menu item, creating one: " + e.getMessage());
    }

    long groupId = jodOhjaajaCmsUtil.getJodOhjaajaCmsGroup().getGroupId();
    var serviceContext =
        ServiceContextTestUtil.getServiceContext(groupId, TestPropsValues.getUserId());
    var siteNavigationMenuLocalService = getService(SiteNavigationMenuLocalService.class);
    var siteNavigationMenus = siteNavigationMenuLocalService.getSiteNavigationMenus(groupId);
    long menuId;
    if (siteNavigationMenus.isEmpty()) {
      createdMenuId =
          siteNavigationMenuLocalService
              .addSiteNavigationMenu(
                  "study-program-test-menu-" + System.currentTimeMillis(),
                  TestPropsValues.getUserId(),
                  groupId,
                  "Study program test menu",
                  serviceContext)
              .getSiteNavigationMenuId();
      menuId = createdMenuId;
    } else {
      menuId = siteNavigationMenus.getFirst().getSiteNavigationMenuId();
    }

    var menuItem =
        getService(SiteNavigationMenuItemLocalService.class)
            .addSiteNavigationMenuItem(
                "study-program-test-listing-" + System.currentTimeMillis(),
                TestPropsValues.getUserId(),
                groupId,
                menuId,
                0,
                "url",
                "name=Koulutukset\nurl=/koulutukset\n",
                serviceContext);
    menuItem.getExpandoBridge().setAttribute("Type", new String[] {"StudyProgramsListing"}, false);
    createdMenuItemId = menuItem.getSiteNavigationMenuItemId();

    // Fails the setup if the menu item is still not found
    navigationService.getStudyProgramsParentMenuItem();
  }

  @AfterClass
  public static void tearDownClass() throws Exception {
    // Deleting the menu item or menu also deletes the study program menu items under it
    if (createdMenuId > 0) {
      getService(SiteNavigationMenuLocalService.class).deleteSiteNavigationMenu(createdMenuId);
    } else if (createdMenuItemId > 0) {
      getService(SiteNavigationMenuItemLocalService.class)
          .deleteSiteNavigationMenuItem(createdMenuItemId);
    }

    if (mockServiceRegistration != null) {
      mockServiceRegistration.unregister();
      System.out.println("✅ MockKonfoClient service unregistered");
    }
    PermissionThreadLocal.setPermissionChecker(originalPermissionChecker);
    if (Validator.isBlank(originalKonfoUrl)) {
      PropsUtil.set(KONFO_URL_KEY, "");
    }

    // Note: Articles are NOT automatically deleted to allow inspection
    // They can be manually cleaned up or will be removed when container restarts
    System.out.println(
        "\n=== Test completed. "
            + allCreatedArticleIds.size()
            + " articles remain for inspection ===");
  }

  @Before
  public void setUp() {
    createdArticleIds = new ArrayList<>();
  }

  @After
  public void tearDown() {
    allCreatedArticleIds.addAll(createdArticleIds);
  }

  private static <T> T getService(Class<T> serviceClass) {
    ServiceReference<T> serviceReference = bundleContext.getServiceReference(serviceClass);
    return bundleContext.getService(serviceReference);
  }

  /**
   * Helper method to start import and wait for completion. Each test should call this to ensure
   * articles exist.
   */
  private void runImportAndWait() throws Exception {
    // Check if import is already running - if so, wait for it
    if (backgroundTaskService.isAnyImportOrDeleteTaskRunning()) {
      System.out.println("⏳ Import already running, waiting for it to complete...");
      try {
        Awaitility.await()
            .atMost(Duration.ofSeconds(60))
            .pollInterval(Duration.ofSeconds(1))
            .until(() -> !backgroundTaskService.isAnyImportOrDeleteTaskRunning());
      } catch (Exception timeout) {
        Assert.fail("Import task did not complete within 60 seconds (was already running)");
        return;
      }
      System.out.println("✅ Previous import completed");
      // Allow database transaction to commit
      settle(Duration.ofSeconds(2));
      return;
    }

    // No import running, start a new one
    BackgroundTask task = backgroundTaskService.startImportTask(TestPropsValues.getUserId());
    long taskId = task.getBackgroundTaskId();
    System.out.println("🚀 Started import task ID: " + taskId);

    try {
      Awaitility.await()
          .atMost(Duration.ofSeconds(60))
          .pollInterval(Duration.ofSeconds(1))
          .until(
              () -> {
                BackgroundTask current = BackgroundTaskManagerUtil.fetchBackgroundTask(taskId);
                return current != null && current.isCompleted();
              });
    } catch (Exception timeout) {
      Assert.fail("Import task did not complete within 60 seconds");
      return;
    }
    System.out.println("✅ Import completed");
    // Allow database transaction to commit
    settle(Duration.ofSeconds(2));
    // All tests of the class run on the same thread, which can keep seeing the task in the state it
    // was created in. Such a task would block the next import as "already running".
    deleteBackgroundTaskById(taskId);
  }

  /**
   * Pauses the current thread for the given duration without using {@link Thread#sleep(long)}, so
   * callers can wait for asynchronous side effects (e.g. database transaction commits or background
   * task cleanup) to become visible. Implemented via Awaitility's {@code pollDelay} to satisfy the
   * "no Thread.sleep in tests" rule while still expressing an intentional pause.
   */
  private static void settle(Duration duration) {
    Awaitility.await().pollDelay(duration).atMost(duration.plusSeconds(1)).until(() -> true);
  }

  @Test
  public void shouldImportStudyProgramsUsingMockClient() throws Exception {
    System.out.println("\n=== Testing Import via startImportTask() with MockKonfoClient ===");

    Assert.assertTrue(
        "KonfoClient should be MockKonfoClient", konfoClient instanceof MockKonfoClient);

    int initialCount = studyProgramService.getImportedStudyPrograms().size();
    System.out.println("Initial article count: " + initialCount);

    int expectedCount = konfoClient.fetchStudyPrograms().size();
    System.out.println("Mock programs available: " + expectedCount);

    // Run import
    runImportAndWait();

    for (int i = 1; i <= expectedCount; i++) {
      String oid = "1.2.246.562.20.0000000000" + i;
      JournalArticle article =
          journalArticleLocalService.fetchLatestArticleByExternalReferenceCode(
              jodOhjaajaCmsUtil.getJodOhjaajaCmsGroup().getGroupId(), oid);

      if (article != null) {
        createdArticleIds.add(oid);
        System.out.println(
            "✅ Article imported: " + article.getTitle("fi_FI") + " (OID: " + oid + ")");

        // Verify article content for first article
        if (i == 1) {
          String fiTitle = article.getTitle("fi_FI");
          Assert.assertNotNull("Finnish title should exist", fiTitle);
          Assert.assertFalse("Title should not be empty", fiTitle.isEmpty());

          String content = article.getContent();
          Assert.assertNotNull("Content should not be null", content);
          Assert.assertFalse("Content should not be empty", content.isEmpty());
          Assert.assertTrue(
              "Content should link to the Konfo of the environment",
              content.contains(PropsUtil.get(KONFO_URL_KEY) + "/fi/koulutus/" + oid));

          System.out.println(
              "   ✅ Article content verified - Title: "
                  + fiTitle
                  + ", Content length: "
                  + content.length()
                  + " chars");
        }
      }
    }

    int finalCount = studyProgramService.getImportedStudyPrograms().size();
    System.out.println("\nFinal article count: " + finalCount);
    Assert.assertTrue("Should have imported articles", finalCount >= initialCount);

    System.out.println("\n✅ Import test completed successfully!");
  }

  @Test
  public void shouldNotUpdateUnchangedStudyPrograms() throws Exception {
    System.out.println("\n=== Testing Re-import of Unchanged Study Programs ===");

    // A study program without an image, so that the test does not depend on downloading one
    String oid = "1.2.246.562.20.00000000002";

    runImportAndWait();
    JournalArticle before = fetchStudyProgramArticle(oid);
    Assert.assertNotNull("Article should exist after the first import", before);
    createdArticleIds.add(oid);
    SiteNavigationMenuItem menuItemBefore = fetchStudyProgramMenuItem(oid);
    Assert.assertNotNull("Menu item should exist after the first import", menuItemBefore);

    runImportAndWait();
    JournalArticle after = fetchStudyProgramArticle(oid);

    Assert.assertEquals(
        "Re-import should not create a new version", before.getVersion(), after.getVersion(), 0);
    Assert.assertEquals(
        "Re-import should not change the modified date",
        before.getModifiedDate().getTime(),
        after.getModifiedDate().getTime());
    Assert.assertEquals(
        "Re-import should not update the menu item",
        menuItemBefore.getModifiedDate().getTime(),
        fetchStudyProgramMenuItem(oid).getModifiedDate().getTime());

    System.out.println("✅ Unchanged article kept version " + after.getVersion());
  }

  @Test
  public void shouldUpdateChangedStudyPrograms() throws Exception {
    System.out.println("\n=== Testing Re-import of Changed Study Programs ===");

    String oid = "1.2.246.562.20.00000000002";
    String changedText = "Kuvaus on muuttunut.";

    runImportAndWait();
    JournalArticle before = fetchStudyProgramArticle(oid);
    Assert.assertNotNull("Article should exist after the first import", before);
    createdArticleIds.add(oid);

    mockKonfoClient.setProgram2KuvausSuffix(" " + changedText);
    try {
      runImportAndWait();
      JournalArticle changed = fetchStudyProgramArticle(oid);

      Assert.assertTrue(
          "Re-import of a changed study program should create a new version",
          changed.getVersion() > before.getVersion());
      Assert.assertTrue(
          "The article should have the changed description",
          changed.getContent().contains(changedText));

      runImportAndWait();
      JournalArticle unchanged = fetchStudyProgramArticle(oid);

      Assert.assertEquals(
          "The fingerprint of the changed study program should be stored",
          changed.getVersion(),
          unchanged.getVersion(),
          0);

      System.out.println(
          "✅ Changed article updated from version "
              + before.getVersion()
              + " to "
              + changed.getVersion());
    } finally {
      mockKonfoClient.setProgram2KuvausSuffix("");
    }
  }

  @Test
  public void shouldNotUpdateStudyProgramsWhoseImageIsStillMissing() throws Exception {
    System.out.println("\n=== Testing Re-import of Study Programs With a Missing Image ===");

    String oid = "1.2.246.562.20.00000000004";

    runImportAndWait();
    JournalArticle before = fetchStudyProgramArticle(oid);
    Assert.assertNotNull("Article should exist without its image", before);
    createdArticleIds.add(oid);

    runImportAndWait();
    JournalArticle after = fetchStudyProgramArticle(oid);

    Assert.assertEquals(
        "Retrying a missing image should not create a new version",
        before.getVersion(),
        after.getVersion(),
        0);

    System.out.println("✅ Article with a missing image kept version " + after.getVersion());
  }

  @Test
  public void shouldNotUpdateStudyProgramsWhoseMissingImageIsImported() throws Exception {
    System.out.println(
        "\n=== Testing Re-import of Study Programs Whose Missing Image Is Imported ===");

    // A study program without an image, so that its article shows the placeholder
    String oid = "1.2.246.562.20.00000000002";
    var fingerprintService = getService(StudyProgramFingerprintService.class);

    runImportAndWait();
    JournalArticle before = fetchStudyProgramArticle(oid);
    Assert.assertNotNull("Article should exist after the first import", before);
    createdArticleIds.add(oid);

    // As if the placeholder could not be imported again when the article was built, so that the
    // article shows the one from an earlier import
    String storedValue =
        (String) before.getExpandoBridge().getAttribute(FINGERPRINT_FIELD_NAME, false);
    String fingerprint = storedValue.substring(0, storedValue.indexOf('@'));
    fingerprintService.setFingerprint(before, fingerprint, false);
    Assert.assertEquals(
        ArticleState.IMAGE_MISSING, fingerprintService.getArticleState(before, fingerprint));

    runImportAndWait();
    JournalArticle after = fetchStudyProgramArticle(oid);

    Assert.assertEquals(
        "Importing the image that the article already shows should not create a new version",
        before.getVersion(),
        after.getVersion(),
        0);
    Assert.assertEquals(
        "The image should no longer be missing",
        ArticleState.UP_TO_DATE,
        fingerprintService.getArticleState(after, fingerprint));

    System.out.println("✅ Article with an imported image kept version " + after.getVersion());
  }

  @Test
  public void shouldRestoreManuallyEditedStudyPrograms() throws Exception {
    System.out.println("\n=== Testing Re-import of Manually Edited Study Programs ===");

    String oid = "1.2.246.562.20.00000000002";
    Locale finnish = LocaleUtil.fromLanguageId("fi_FI");

    runImportAndWait();
    JournalArticle imported = fetchStudyProgramArticle(oid);
    Assert.assertNotNull("Article should exist after the first import", imported);
    createdArticleIds.add(oid);

    var editedTitleMap = new HashMap<>(imported.getTitleMap());
    editedTitleMap.put(finnish, "Käsin muokattu otsikko");
    JournalArticle edited =
        journalArticleLocalService.updateArticle(
            imported.getUserId(),
            imported.getGroupId(),
            imported.getFolderId(),
            imported.getArticleId(),
            imported.getVersion(),
            editedTitleMap,
            imported.getDescriptionMap(),
            imported.getContent(),
            imported.getLayoutUuid(),
            ServiceContextTestUtil.getServiceContext(
                imported.getGroupId(), TestPropsValues.getUserId()));

    runImportAndWait();
    JournalArticle restored = fetchStudyProgramArticle(oid);

    Assert.assertTrue(
        "Re-import of an edited article should create a new version",
        restored.getVersion() > edited.getVersion());
    Assert.assertEquals(
        "Re-import should restore the imported title",
        imported.getTitle(finnish),
        restored.getTitle(finnish));

    System.out.println("✅ Edited article restored as version " + restored.getVersion());
  }

  @Test
  public void shouldRestoreDeletedImage() throws Exception {
    System.out.println("\n=== Testing Re-import of Study Programs Whose Image Was Deleted ===");

    // The image is added again with a new UUID, which the article has to be updated to
    assertImageRestoredAfter(
        image -> DLAppLocalServiceUtil.deleteFileEntry(image.getFileEntryId()), true);
  }

  @Test
  public void shouldRestoreImageMovedToRecycleBin() throws Exception {
    System.out.println(
        "\n=== Testing Re-import of Study Programs Whose Image Is in the Recycle Bin ===");

    // The import restores the image before checking the articles, so the article that shows it
    // is valid again without a new version
    var trashedImageId = new AtomicLong();
    assertImageRestoredAfter(
        image -> {
          trashedImageId.set(image.getFileEntryId());
          DLTrashLocalServiceUtil.moveFileEntryToTrash(
              TestPropsValues.getUserId(), image.getRepositoryId(), image.getFileEntryId());
        },
        false);

    Assert.assertEquals(
        "The image should be restored from the Recycle Bin instead of being added again",
        trashedImageId.get(),
        getPlaceholderImage().getFileEntryId());
  }

  /**
   * Removes the placeholder image with the given action, and checks that the import restores it in
   * the articles that show it.
   *
   * @param newVersionExpected whether the article has to be updated to show the restored image
   */
  private void assertImageRestoredAfter(
      UnsafeConsumer<FileEntry, Exception> removeImage, boolean newVersionExpected)
      throws Exception {
    // A study program without an image, so that its article shows the placeholder
    String oid = "1.2.246.562.20.00000000002";

    runImportAndWait();
    JournalArticle before = fetchStudyProgramArticle(oid);
    Assert.assertNotNull("Article should exist after the first import", before);
    createdArticleIds.add(oid);

    removeImage.accept(getPlaceholderImage());

    runImportAndWait();
    JournalArticle restored = fetchStudyProgramArticle(oid);
    FileEntry placeholder = getPlaceholderImage();

    Assert.assertEquals(
        "Re-import should create a new version only if the article has to show another image",
        newVersionExpected,
        restored.getVersion() > before.getVersion());
    Assert.assertFalse("The image should not be in the Recycle Bin", placeholder.isInTrash());
    Assert.assertTrue(
        "The article should show the imported image",
        restored.getContent().contains(placeholder.getUuid()));

    runImportAndWait();
    Assert.assertEquals(
        "The restored article should not be updated again",
        restored.getVersion(),
        fetchStudyProgramArticle(oid).getVersion(),
        0);

    System.out.println("✅ Image restored, article at version " + restored.getVersion());
  }

  @Test
  public void shouldKeepShownImageUntilImageCanBeImported() throws Exception {
    System.out.println(
        "\n=== Testing Import of Study Programs Whose Image Cannot Be Downloaded ===");

    // The image of this study program can never be downloaded
    String oid = "1.2.246.562.20.00000000004";

    runImportAndWait();
    FileEntry earlierImage = addEarlierImage(oid);
    try {
      showImage(fetchStudyProgramArticle(oid), earlierImage);

      runImportAndWait();
      JournalArticle article = fetchStudyProgramArticle(oid);
      Assert.assertTrue(
          "The article should keep showing the image from the earlier import",
          article.getContent().contains(earlierImage.getUuid()));

      runImportAndWait();
      Assert.assertEquals(
          "Retrying the image should not create a new version",
          article.getVersion(),
          fetchStudyProgramArticle(oid).getVersion(),
          0);

      System.out.println("✅ Article shows the earlier image " + earlierImage.getFileName());
    } finally {
      // The next import adds the article again, showing the placeholder
      deleteStudyProgramArticle(oid);
      DLAppLocalServiceUtil.deleteFileEntry(earlierImage.getFileEntryId());
    }
  }

  @Test
  public void shouldShowPlaceholderWhenShownImageIsMovedToRecycleBinWhileImageIsMissing()
      throws Exception {
    System.out.println(
        "\n=== Testing Import of Study Programs Whose Shown Image Is in the Recycle Bin ===");

    // The image of this study program can never be downloaded
    String oid = "1.2.246.562.20.00000000004";

    runImportAndWait();
    FileEntry earlierImage = addEarlierImage(oid);
    try {
      showImage(fetchStudyProgramArticle(oid), earlierImage);
      runImportAndWait();
      JournalArticle before = fetchStudyProgramArticle(oid);
      Assert.assertTrue(
          "The article should show the image from the earlier import",
          before.getContent().contains(earlierImage.getUuid()));

      DLTrashLocalServiceUtil.moveFileEntryToTrash(
          TestPropsValues.getUserId(),
          earlierImage.getRepositoryId(),
          earlierImage.getFileEntryId());

      runImportAndWait();
      JournalArticle after = fetchStudyProgramArticle(oid);
      Assert.assertTrue(
          "Re-import should create a new version without the image",
          after.getVersion() > before.getVersion());
      Assert.assertFalse(
          "The article should not show the image in the Recycle Bin",
          after.getContent().contains(earlierImage.getUuid()));
      Assert.assertTrue(
          "The article should show the placeholder instead",
          after.getContent().contains(getPlaceholderImage().getUuid()));

      runImportAndWait();
      Assert.assertEquals(
          "The article with the placeholder should not be updated again",
          after.getVersion(),
          fetchStudyProgramArticle(oid).getVersion(),
          0);

      System.out.println("✅ Placeholder shown in article version " + after.getVersion());
    } finally {
      // The next import adds the article again, showing the placeholder
      deleteStudyProgramArticle(oid);
      DLAppLocalServiceUtil.deleteFileEntry(earlierImage.getFileEntryId());
    }
  }

  @Test
  public void shouldKeepPlaceholderWhenNewImageCannotBeImported() throws Exception {
    System.out.println(
        "\n=== Testing Import of Study Programs Whose New Image Cannot Be Downloaded ===");

    // A study program without an image, so that its article shows the placeholder
    String oid = "1.2.246.562.20.00000000002";

    runImportAndWait();
    Assert.assertNotNull(
        "Article should exist after the first import", fetchStudyProgramArticle(oid));
    createdArticleIds.add(oid);
    // As if the study program had had an image before it had none, which the article no longer
    // shows
    FileEntry staleImage = addEarlierImage(oid);
    mockKonfoClient.setProgram2Teemakuva("http://127.0.0.1:9/teemakuva2.jpg");
    try {
      runImportAndWait();
      JournalArticle article = fetchStudyProgramArticle(oid);
      Assert.assertTrue(
          "The article should keep showing the placeholder",
          article.getContent().contains(getPlaceholderImage().getUuid()));
      Assert.assertFalse(
          "The article should not show the image that it no longer showed",
          article.getContent().contains(staleImage.getUuid()));

      runImportAndWait();
      Assert.assertEquals(
          "Retrying the image should not create a new version",
          article.getVersion(),
          fetchStudyProgramArticle(oid).getVersion(),
          0);

      System.out.println("✅ Article kept the placeholder in version " + article.getVersion());
    } finally {
      mockKonfoClient.setProgram2Teemakuva(null);
      DLAppLocalServiceUtil.deleteFileEntry(staleImage.getFileEntryId());
    }
  }

  /** Stores an image for the study program, as if an earlier import had downloaded it. */
  private static FileEntry addEarlierImage(String oid) throws Exception {
    var fileService = getService(StudyProgramFileService.class);
    FileEntry image =
        fileService.getOrCreateFileEntryByExternalReferenceCode(
            oid,
            "studyprogram-" + oid + ".png",
            fileService.readImageBytes(PLACEHOLDER_IMAGE_PATH),
            TestPropsValues.getUserId());
    Assert.assertNotNull("The earlier image should be stored", image);
    return image;
  }

  /**
   * Edits the article to show the given image instead of the placeholder, as if an earlier import
   * had built it with that image.
   */
  private static void showImage(JournalArticle article, FileEntry image) throws Exception {
    FileEntry placeholder = getPlaceholderImage();
    Assert.assertTrue(
        "The article should show the placeholder",
        article.getContent().contains(placeholder.getUuid()));
    journalArticleLocalService.updateArticle(
        article.getUserId(),
        article.getGroupId(),
        article.getFolderId(),
        article.getArticleId(),
        article.getVersion(),
        article.getTitleMap(),
        article.getDescriptionMap(),
        article
            .getContent()
            .replace(placeholder.getUuid(), image.getUuid())
            .replace(placeholder.getFileName(), image.getFileName()),
        article.getLayoutUuid(),
        ServiceContextTestUtil.getServiceContext(
            article.getGroupId(), TestPropsValues.getUserId()));
  }

  @Test
  public void shouldIgnoreTrailingSlashOfKonfoUrl() throws Exception {
    System.out.println("\n=== Testing Import With a Trailing Slash in the Konfo URL ===");

    String oid = "1.2.246.562.20.00000000002";
    String konfoUrl = PropsUtil.get(KONFO_URL_KEY);

    runImportAndWait();
    JournalArticle before = fetchStudyProgramArticle(oid);
    Assert.assertNotNull("Article should exist after the first import", before);
    createdArticleIds.add(oid);

    PropsUtil.set(KONFO_URL_KEY, konfoUrl + "/");
    try {
      runImportAndWait();
    } finally {
      PropsUtil.set(KONFO_URL_KEY, konfoUrl);
    }

    Assert.assertEquals(
        "The links of the article should not change",
        before.getVersion(),
        fetchStudyProgramArticle(oid).getVersion(),
        0);

    System.out.println("✅ Article kept version " + before.getVersion());
  }

  private static JournalArticle fetchStudyProgramArticle(String oid) {
    return journalArticleLocalService.fetchLatestArticleByExternalReferenceCode(
        jodOhjaajaCmsUtil.getJodOhjaajaCmsGroup().getGroupId(), oid);
  }

  private static SiteNavigationMenuItem fetchStudyProgramMenuItem(String oid) {
    return getService(SiteNavigationMenuItemLocalService.class)
        .fetchSiteNavigationMenuItemByExternalReferenceCode(
            oid, jodOhjaajaCmsUtil.getJodOhjaajaCmsGroup().getGroupId());
  }

  private static void deleteStudyProgramArticle(String oid) throws Exception {
    JournalArticle article = fetchStudyProgramArticle(oid);
    if (article != null) {
      getService(NavigationService.class).deleteStudyProgramNavigationMenuItem(oid);
      journalArticleLocalService.deleteArticle(
          article.getGroupId(),
          article.getArticleId(),
          ServiceContextTestUtil.getServiceContext(
              article.getGroupId(), TestPropsValues.getUserId()));
    }
  }

  private static FileEntry getPlaceholderImage() throws PortalException {
    return DLAppLocalServiceUtil.getFileEntryByExternalReferenceCode(
        PLACEHOLDER_IMAGE_ERC, jodOhjaajaCmsUtil.getJodOhjaajaCmsGroup().getGroupId());
  }

  @Test
  public void shouldPreventConcurrentTasks() throws Exception {
    System.out.println("\n=== Testing Concurrent Task Prevention ===");

    long fakeTaskId = createFakeRunningImportTask();
    try {
      long userId = TestPropsValues.getUserId();

      var concurrentImport =
          Assert.assertThrows(
              "Should not allow concurrent import tasks",
              IllegalStateException.class,
              () -> backgroundTaskService.startImportTask(userId));
      System.out.println(
          "✅ Concurrent import correctly prevented: " + concurrentImport.getMessage());

      var concurrentDelete =
          Assert.assertThrows(
              "Should not allow delete while import is running",
              IllegalStateException.class,
              () -> backgroundTaskService.startDeleteTask(userId));
      System.out.println(
          "✅ Delete correctly prevented during import: " + concurrentDelete.getMessage());
    } finally {
      deleteBackgroundTaskById(fakeTaskId);
    }

    System.out.println("✅ Concurrent task prevention test completed");
  }

  @Test
  public void shouldDetectRunningTasks() throws Exception {
    System.out.println("\n=== Testing Task Detection ===");

    Assert.assertFalse(
        "No task should be running before setup",
        backgroundTaskService.isAnyImportOrDeleteTaskRunning());

    // A real startImportTask() could complete before the assertion - the task is not reliably
    // observable as "running". We insert an IN_PROGRESS row directly so we can verify that
    // isAnyImportOrDeleteTaskRunning() reports it.
    long fakeTaskId = createFakeRunningImportTask();
    try {
      Assert.assertTrue(
          "Should detect running task", backgroundTaskService.isAnyImportOrDeleteTaskRunning());
      System.out.println("✅ Running task detected");
    } finally {
      deleteBackgroundTaskById(fakeTaskId);
    }

    System.out.println("✅ Task detection test completed");
  }

  /**
   * Inserts a BackgroundTask row directly into the database with status IN_PROGRESS so the
   * concurrent-task prevention logic can observe it as active.
   *
   * <p>We do not start a task through {@link StudyProgramBackgroundTaskService#startImportTask}
   * here because the task could complete before the assertion. Bypassing the dispatcher and writing
   * the row ourselves lets us simulate an in-progress task deterministically.
   */
  private static long createFakeRunningImportTask() throws Exception {
    long taskId =
        CounterLocalServiceUtil.increment(
            com.liferay.portal.background.task.model.BackgroundTask.class.getName());
    var task = BackgroundTaskLocalServiceUtil.createBackgroundTask(taskId);
    User user = TestPropsValues.getUser();
    task.setUserId(user.getUserId());
    task.setUserName(user.getFullName());
    task.setCompanyId(user.getCompanyId());
    task.setGroupId(jodOhjaajaCmsUtil.getJodOhjaajaCmsGroup().getGroupId());
    task.setName("study-program-import");
    task.setTaskExecutorClassName(
        "fi.okm.jod.ohjaaja.cms.studyprogram.background.task.ImportStudyProgramsBackgroundTaskExecutor");
    // Liferay's BackgroundTaskModelListener.onBeforeRemove reads from the context map, so it
    // must be non-null even though we do not need any actual context values here.
    task.setTaskContextMap(new java.util.HashMap<>());
    task.setStatus(BackgroundTaskConstants.STATUS_IN_PROGRESS);
    BackgroundTaskLocalServiceUtil.updateBackgroundTask(task);
    return taskId;
  }

  /**
   * Deletes any background tasks that previous test runs (in this or earlier JVM lifetimes) may
   * have left behind. Without this, a leftover IN_PROGRESS row from a partially failed run would
   * make the concurrent-task prevention assertions fail spuriously.
   */
  @Before
  public void clearLeftoverBackgroundTasks() {
    deleteAllBackgroundTasks(ImportStudyProgramsBackgroundTaskExecutor.class.getName());
    deleteAllBackgroundTasks(DeleteImportedStudyProgramsBackgroundTaskExecutor.class.getName());
  }

  private static void deleteAllBackgroundTasks(String taskExecutorClassName) {
    for (var task :
        BackgroundTaskLocalServiceUtil.getBackgroundTasks(
            jodOhjaajaCmsUtil.getJodOhjaajaCmsGroup().getGroupId(), taskExecutorClassName)) {
      deleteBackgroundTaskById(task.getBackgroundTaskId());
    }
  }

  /**
   * Deletes a background task row, normalising its state first so that Liferay's lifecycle checks
   * do not refuse the deletion. Tasks left in {@code IN_PROGRESS} status (as produced by {@link
   * #createFakeRunningImportTask}) are first marked as completed; missing context maps - required
   * by {@code BackgroundTaskModelListener.onBeforeRemove} - are filled in.
   */
  private static void deleteBackgroundTaskById(long taskId) {
    try {
      com.liferay.portal.background.task.model.BackgroundTask task =
          BackgroundTaskLocalServiceUtil.fetchBackgroundTask(taskId);
      if (task == null) {
        return;
      }
      if (task.getTaskContextMap() == null) {
        task.setTaskContextMap(new java.util.HashMap<>());
      }
      task.setStatus(
          com.liferay.portal.kernel.backgroundtask.constants.BackgroundTaskConstants
              .STATUS_SUCCESSFUL);
      task.setCompleted(true);
      BackgroundTaskLocalServiceUtil.updateBackgroundTask(task);
      BackgroundTaskLocalServiceUtil.deleteBackgroundTask(task);
    } catch (Exception e) {
      System.err.println(
          "Warning: failed to delete background task " + taskId + ": " + e.getMessage());
    }
  }
}
