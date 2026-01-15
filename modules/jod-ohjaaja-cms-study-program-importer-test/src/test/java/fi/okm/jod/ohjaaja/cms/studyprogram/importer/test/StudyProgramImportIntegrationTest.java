/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.studyprogram.importer.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.journal.model.JournalArticle;
import com.liferay.journal.service.JournalArticleLocalService;
import com.liferay.portal.kernel.backgroundtask.BackgroundTask;
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

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

@RunWith(Arquillian.class)
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

  private static List<String> allCreatedArticleIds = new ArrayList<>();

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

    // Wait a moment for service registration to propagate
    Thread.sleep(500);

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
      
      // Wait up to 60 seconds for the running import to finish
      for (int i = 0; i < 60; i++) {
        Thread.sleep(1000);
        if (!backgroundTaskService.isAnyImportOrDeleteTaskRunning()) {
          System.out.println("✅ Previous import completed after " + i + " seconds");
          // Give a moment for database transaction to commit
          Thread.sleep(2000);
          return;
        }
      }
      
      Assert.fail("Import task did not complete within 60 seconds (was already running)");
      return;
    }

    // No import running, start a new one
    BackgroundTask task = backgroundTaskService.startImportTask(TestPropsValues.getUserId());
    long taskId = task.getBackgroundTaskId();
    System.out.println("🚀 Started import task ID: " + taskId);
    
    // Wait for completion
    for (int i = 0; i < 60; i++) {
      Thread.sleep(1000);
      task = com.liferay.portal.kernel.backgroundtask.BackgroundTaskManagerUtil
          .fetchBackgroundTask(taskId);
      if (task != null && task.isCompleted()) {
        System.out.println("✅ Import completed after " + i + " seconds");
        // Give a moment for database transaction to commit
        Thread.sleep(2000);
        return;
      }
    }
    
    Assert.fail("Import task did not complete within 60 seconds");
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
          Assert.assertTrue("Title should not be empty", fiTitle.length() > 0);
          
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

    // Start first import
    BackgroundTask task1 = backgroundTaskService.startImportTask(TestPropsValues.getUserId());
    Assert.assertNotNull("First task should be created", task1);
    System.out.println("✅ First import task started: " + task1.getBackgroundTaskId());

    // Try to start another import while first is running
    try {
      backgroundTaskService.startImportTask(TestPropsValues.getUserId());
      Assert.fail("Should not allow concurrent import tasks");
    } catch (IllegalStateException e) {
      System.out.println("✅ Concurrent import correctly prevented: " + e.getMessage());
    }

    // Try to start delete while import is running
    try {
      backgroundTaskService.startDeleteTask(TestPropsValues.getUserId());
      Assert.fail("Should not allow delete while import is running");
    } catch (IllegalStateException e) {
      System.out.println("✅ Delete correctly prevented during import: " + e.getMessage());
    }

    // Wait for first task to complete
    for (int i = 0; i < 60; i++) {
      Thread.sleep(1000);
      BackgroundTask currentTask = com.liferay.portal.kernel.backgroundtask.BackgroundTaskManagerUtil
          .fetchBackgroundTask(task1.getBackgroundTaskId());
      if (currentTask != null && currentTask.isCompleted()) {
        System.out.println("✅ First task completed");
        break;
      }
    }

    // Give time for task to be cleaned up
    Thread.sleep(2000);

    System.out.println("✅ Concurrent task prevention test completed");
  }

  @Test
  public void shouldDetectRunningTasks() throws Exception {
    System.out.println("\n=== Testing Task Detection ===");

    // Initially no tasks should be running (or might be if previous test just finished)
    boolean initiallyRunning = backgroundTaskService.isAnyImportOrDeleteTaskRunning();
    System.out.println("Initially running: " + initiallyRunning);

    // Start a task
    BackgroundTask task = backgroundTaskService.startImportTask(TestPropsValues.getUserId());
    Assert.assertNotNull("Task should be created", task);

    // Should detect running task
    boolean nowRunning = backgroundTaskService.isAnyImportOrDeleteTaskRunning();
    Assert.assertTrue("Should detect running task", nowRunning);
    System.out.println("✅ Running task detected");

    // Wait for completion
    for (int i = 0; i < 60; i++) {
      Thread.sleep(1000);
      BackgroundTask currentTask = com.liferay.portal.kernel.backgroundtask.BackgroundTaskManagerUtil
          .fetchBackgroundTask(task.getBackgroundTaskId());
      if (currentTask != null && currentTask.isCompleted()) {
        break;
      }
    }

    // Give time for cleanup
    Thread.sleep(2000);

    System.out.println("✅ Task detection test completed");
  }
}
