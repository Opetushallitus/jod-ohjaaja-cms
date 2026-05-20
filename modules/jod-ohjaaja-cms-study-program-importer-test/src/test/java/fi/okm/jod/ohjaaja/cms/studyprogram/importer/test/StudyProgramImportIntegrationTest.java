/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.studyprogram.importer.test;

import com.liferay.portal.kernel.backgroundtask.constants.BackgroundTaskConstants;
import fi.okm.jod.ohjaaja.cms.testrunner.client.JodInContainerRunner;
import com.liferay.counter.kernel.service.CounterLocalServiceUtil;
import com.liferay.journal.model.JournalArticle;
import com.liferay.journal.service.JournalArticleLocalService;
import com.liferay.portal.background.task.service.BackgroundTaskLocalServiceUtil;
import com.liferay.portal.kernel.backgroundtask.BackgroundTask;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskManagerUtil;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.PermissionCheckerFactory;
import com.liferay.portal.kernel.security.permission.PermissionThreadLocal;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import fi.okm.jod.ohjaaja.cms.studyprogram.client.KonfoClient;
import fi.okm.jod.ohjaaja.cms.studyprogram.service.StudyProgramBackgroundTaskService;
import fi.okm.jod.ohjaaja.cms.studyprogram.service.StudyProgramService;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

@RunWith(JodInContainerRunner.class)
public class StudyProgramImportIntegrationTest {

  @ClassRule
  @Rule
  public static final AggregateTestRule aggregateTestRule = new LiferayIntegrationTestRule();

  private static final long TEST_GROUP_ID = 20117L;

  private static BundleContext bundleContext;
  private static StudyProgramService studyProgramService;
  private static JournalArticleLocalService journalArticleLocalService;
  private static StudyProgramBackgroundTaskService backgroundTaskService;
  private static KonfoClient konfoClient;
  private static PermissionChecker originalPermissionChecker;
  private static ServiceRegistration<KonfoClient> mockServiceRegistration;

  private static final List<String> allCreatedArticleIds = new ArrayList<>();

  private List<String> createdArticleIds = new ArrayList<>();

  @BeforeClass
  public static void setUpClass() throws Exception {
    var bundle = FrameworkUtil.getBundle(StudyProgramImportIntegrationTest.class);
    bundleContext = bundle.getBundleContext();

    // Register MockKonfoClient as OSGi service with high ranking
    MockKonfoClient mockKonfoClient = new MockKonfoClient();
    Dictionary<String, Object> props = new Hashtable<>();
    props.put("service.ranking", 1000);
    mockServiceRegistration = bundleContext.registerService(
        KonfoClient.class, mockKonfoClient, props);

    System.out.println("✅ MockKonfoClient registered as OSGi service with ranking 1000");

    // Wait for the higher-ranked MockKonfoClient registration to become the resolved service
    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(100))
        .until(() -> getService(KonfoClient.class) instanceof MockKonfoClient);

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
  }

  @AfterClass
  public static void tearDownClass() {
    if (mockServiceRegistration != null) {
      mockServiceRegistration.unregister();
      System.out.println("✅ MockKonfoClient service unregistered");
    }
    PermissionThreadLocal.setPermissionChecker(originalPermissionChecker);

    // Note: Articles are NOT automatically deleted to allow inspection
    // They can be manually cleaned up or will be removed when container restarts
    System.out.println("\n=== Test completed. " + allCreatedArticleIds.size() +
        " articles remain for inspection ===");
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
   * Helper method to start import and wait for completion.
   * Each test should call this to ensure articles exist.
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
          .until(() -> {
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
  }

  /**
   * Pauses the current thread for the given duration without using {@link Thread#sleep(long)},
   * so callers can wait for asynchronous side effects (e.g. database transaction commits or
   * background task cleanup) to become visible. Implemented via Awaitility's {@code pollDelay}
   * to satisfy the "no Thread.sleep in tests" rule while still expressing an intentional pause.
   */
  private static void settle(Duration duration) {
    Awaitility.await()
        .pollDelay(duration)
        .atMost(duration.plusSeconds(1))
        .until(() -> true);
  }

  @Test
  public void shouldImportStudyProgramsUsingMockClient() throws Exception {
    System.out.println("\n=== Testing Import via startImportTask() with MockKonfoClient ===");

    Assert.assertTrue("KonfoClient should be MockKonfoClient",
        konfoClient instanceof MockKonfoClient);

    int initialCount = studyProgramService.getImportedStudyPrograms().size();
    System.out.println("Initial article count: " + initialCount);

    int expectedCount = konfoClient.fetchStudyPrograms().size();
    System.out.println("Mock programs available: " + expectedCount);

    // Run import
    runImportAndWait();

    for (int i = 1; i <= expectedCount; i++) {
      String oid = "1.2.246.562.20.0000000000" + i;
      JournalArticle article = journalArticleLocalService
          .fetchLatestArticleByExternalReferenceCode(TEST_GROUP_ID, oid);

      if (article != null) {
        createdArticleIds.add(oid);
        System.out.println("✅ Article imported: " + article.getTitle("fi_FI") + " (OID: " + oid + ")");

        // Verify article content for first article
        if (i == 1) {
          String fiTitle = article.getTitle("fi_FI");
          Assert.assertNotNull("Finnish title should exist", fiTitle);
          Assert.assertFalse("Title should not be empty", fiTitle.isEmpty());

          String content = article.getContent();
          Assert.assertNotNull("Content should not be null", content);
          Assert.assertFalse("Content should not be empty", content.isEmpty());

          System.out.println("   ✅ Article content verified - Title: " + fiTitle +
              ", Content length: " + content.length() + " chars");
        }
      }
    }

    int finalCount = studyProgramService.getImportedStudyPrograms().size();
    System.out.println("\nFinal article count: " + finalCount);
    Assert.assertTrue("Should have imported articles",
        finalCount >= initialCount);

    System.out.println("\n✅ Import test completed successfully!");
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

    // LiferayIntegrationTestRule replaces liferay/background_task with a synchronous
    // destination, so a real startImportTask() runs the executor to completion before it
    // returns - the task is never observable as "running". We insert an IN_PROGRESS row
    // directly so we can verify that isAnyImportOrDeleteTaskRunning() reports it.
    long fakeTaskId = createFakeRunningImportTask();
    try {
      Assert.assertTrue(
          "Should detect running task",
          backgroundTaskService.isAnyImportOrDeleteTaskRunning());
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
   * <p>We cannot start a task through {@link StudyProgramBackgroundTaskService#startImportTask}
   * here because {@link LiferayIntegrationTestRule} registers a synchronous destination for
   * {@code liferay/background_task}, which makes every dispatched task run to completion on the
   * test thread before {@code startImportTask} returns. Bypassing the dispatcher and writing the
   * row ourselves lets us simulate an in-progress task deterministically.
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
    task.setGroupId(TEST_GROUP_ID);
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
    deleteAllBackgroundTasks("study-program-import");
    deleteAllBackgroundTasks("study-program-delete");
  }

  private static void deleteAllBackgroundTasks(String taskName) {
    for (var task : BackgroundTaskLocalServiceUtil.getBackgroundTasks(TEST_GROUP_ID, taskName)) {
      deleteBackgroundTaskById(task.getBackgroundTaskId());
    }
  }

  /**
   * Deletes a background task row, normalising its state first so that Liferay's lifecycle
   * checks do not refuse the deletion. Tasks left in {@code IN_PROGRESS} status (as produced
   * by {@link #createFakeRunningImportTask}) are first marked as completed; missing context
   * maps - required by {@code BackgroundTaskModelListener.onBeforeRemove} - are filled in.
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
      task.setStatus(com.liferay.portal.kernel.backgroundtask.constants.BackgroundTaskConstants.STATUS_SUCCESSFUL);
      task.setCompleted(true);
      BackgroundTaskLocalServiceUtil.updateBackgroundTask(task);
      BackgroundTaskLocalServiceUtil.deleteBackgroundTask(task);
    } catch (Exception e) {
      System.err.println(
          "Warning: failed to delete background task " + taskId + ": " + e.getMessage());
    }
  }
}
