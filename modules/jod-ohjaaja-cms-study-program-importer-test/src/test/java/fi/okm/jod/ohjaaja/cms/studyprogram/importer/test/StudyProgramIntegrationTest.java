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
import com.liferay.dynamic.data.mapping.model.DDMStructure;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.PermissionCheckerFactory;
import com.liferay.portal.kernel.security.permission.PermissionThreadLocal;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import fi.okm.jod.ohjaaja.cms.studyprogram.service.StudyProgramStructureService;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;

/**
 * Tests for DDM Structure management.
 * Verifies that study program DDM structures are correctly created and configured.
 */
@RunWith(Arquillian.class)
public class StudyProgramIntegrationTest {

  @ClassRule
  @Rule
  public static final AggregateTestRule aggregateTestRule = new LiferayIntegrationTestRule();

  private static BundleContext bundleContext;
  private static StudyProgramStructureService studyProgramStructureService;
  private static PermissionChecker originalPermissionChecker;

  @BeforeClass
  public static void setUpClass() throws Exception {
    var bundle = FrameworkUtil.getBundle(StudyProgramIntegrationTest.class);
    bundleContext = bundle.getBundleContext();

    studyProgramStructureService = getService(StudyProgramStructureService.class);

    originalPermissionChecker = PermissionThreadLocal.getPermissionChecker();
    
    var permissionCheckerFactory = getService(PermissionCheckerFactory.class);
    User adminUser = TestPropsValues.getUser();
    PermissionChecker permissionChecker = permissionCheckerFactory.create(adminUser);
    PermissionThreadLocal.setPermissionChecker(permissionChecker);
  }

  @AfterClass
  public static void tearDownClass() {
    PermissionThreadLocal.setPermissionChecker(originalPermissionChecker);
  }

  private static <T> T getService(Class<T> serviceClass) {
    ServiceReference<T> serviceReference = bundleContext.getServiceReference(serviceClass);
    return bundleContext.getService(serviceReference);
  }

  @Test
  public void shouldGetOrCreateDDMStructure() {
    DDMStructure structure = studyProgramStructureService.getOrCreateDDMStructure(true);

    Assert.assertNotNull("DDM structure should not be null", structure);
    Assert.assertNotNull("Structure key should not be null", structure.getStructureKey());
    System.out.println("✅ DDM Structure: " + structure.getStructureKey());
  }

  @Test
  public void shouldGetExistingDDMStructureWithoutCreating() {
    DDMStructure createdStructure = studyProgramStructureService.getOrCreateDDMStructure(true);
    Assert.assertNotNull("Created structure should not be null", createdStructure);

    DDMStructure existingStructure = studyProgramStructureService.getOrCreateDDMStructure(false);

    Assert.assertNotNull("Existing structure should be found", existingStructure);
    Assert.assertEquals("Structure IDs should match", 
        createdStructure.getStructureId(), existingStructure.getStructureId());
    System.out.println("✅ Existing DDM Structure retrieved");
  }

  @Test
  public void shouldVerifyDDMStructureHasCorrectClassNameId() {
    DDMStructure structure = studyProgramStructureService.getOrCreateDDMStructure(true);
    Assert.assertNotNull("Structure should not be null", structure);

    long expectedClassNameId = com.liferay.portal.kernel.util.PortalUtil.getClassNameId(
        com.liferay.journal.model.JournalArticle.class);
    
    Assert.assertEquals("Structure should have JournalArticle class name ID",
        expectedClassNameId, structure.getClassNameId());
    
    System.out.println("✅ DDM Structure ClassNameId: " + structure.getClassNameId());
  }

  @Test
  public void shouldVerifyDDMStructureHasDefinition() {
    DDMStructure structure = studyProgramStructureService.getOrCreateDDMStructure(true);
    Assert.assertNotNull("Structure should not be null", structure);

    String definition = structure.getDefinition();
    Assert.assertNotNull("Structure definition should not be null", definition);
    Assert.assertFalse("Structure definition should not be empty", definition.isEmpty());
    
    System.out.println("✅ DDM Structure definition length: " + definition.length());
  }
}
