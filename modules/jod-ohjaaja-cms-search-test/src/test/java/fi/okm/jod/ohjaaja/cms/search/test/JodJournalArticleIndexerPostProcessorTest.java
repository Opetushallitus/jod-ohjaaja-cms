/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.search.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.document.library.kernel.service.DLAppLocalService;
import com.liferay.dynamic.data.mapping.model.DDMForm;
import com.liferay.dynamic.data.mapping.model.DDMFormField;
import com.liferay.dynamic.data.mapping.model.DDMFormLayout;
import com.liferay.dynamic.data.mapping.model.DDMFormLayoutColumn;
import com.liferay.dynamic.data.mapping.model.DDMFormLayoutPage;
import com.liferay.dynamic.data.mapping.model.DDMFormLayoutRow;
import com.liferay.dynamic.data.mapping.model.DDMStructure;
import com.liferay.dynamic.data.mapping.model.LocalizedValue;
import com.liferay.dynamic.data.mapping.service.DDMStructureLocalService;
import com.liferay.dynamic.data.mapping.storage.DDMFormFieldValue;
import com.liferay.dynamic.data.mapping.storage.DDMFormValues;
import com.liferay.dynamic.data.mapping.util.DDM;
import com.liferay.journal.model.JournalArticle;
import com.liferay.journal.service.JournalArticleLocalService;
import com.liferay.journal.util.JournalConverter;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.search.Field;
import com.liferay.portal.kernel.search.IndexerPostProcessor;
import com.liferay.portal.kernel.search.IndexerRegistry;
import com.liferay.portal.kernel.search.SearchContext;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.PermissionCheckerFactory;
import com.liferay.portal.kernel.security.permission.PermissionThreadLocal;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.test.util.ServiceContextTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import fi.okm.jod.ohjaaja.cms.search.JodJournalArticleIndexerPostProcessor;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Set;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.After;
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
 * Integration tests for JodJournalArticleIndexerPostProcessor.
 *
 * Tests IndexerPostProcessor registration and basic article indexing.
 */
@RunWith(Arquillian.class)
public class JodJournalArticleIndexerPostProcessorTest {

  @ClassRule @Rule
  public static final LiferayIntegrationTestRule liferayIntegrationTestRule =
      new LiferayIntegrationTestRule();

  private static final long TEST_GROUP_ID = 20117L;

  private static BundleContext bundleContext;
  private static PermissionChecker originalPermissionChecker;
  private static JournalArticleLocalService journalArticleLocalService;
  private static DDMStructureLocalService ddmStructureLocalService;
  private static IndexerRegistry indexerRegistry;

  private JournalArticle testArticle;
  private DDMStructure testStructure;

  @BeforeClass
  public static void setUpClass() throws Exception {
    var bundle = FrameworkUtil.getBundle(JodJournalArticleIndexerPostProcessorTest.class);
    bundleContext = bundle.getBundleContext();

    originalPermissionChecker = PermissionThreadLocal.getPermissionChecker();
    var permissionCheckerFactoryRef =
        bundleContext.getServiceReference(PermissionCheckerFactory.class);
    var permissionCheckerFactory = bundleContext.getService(permissionCheckerFactoryRef);
    var adminUser = TestPropsValues.getUser();
    var permissionChecker = permissionCheckerFactory.create(adminUser);
    PermissionThreadLocal.setPermissionChecker(permissionChecker);
    bundleContext.ungetService(permissionCheckerFactoryRef);

    journalArticleLocalService = getService(JournalArticleLocalService.class);
    ddmStructureLocalService = getService(DDMStructureLocalService.class);
    indexerRegistry = getService(IndexerRegistry.class);


  }

  @AfterClass
  public static void tearDownClass() {
    PermissionThreadLocal.setPermissionChecker(originalPermissionChecker);
  }

  @After
  public void tearDown() throws Exception {
    if (testArticle != null) {
      try {
        journalArticleLocalService.deleteArticle(testArticle);
      } catch (Exception e) {
        System.err.println("Failed to clean up test article: " + e.getMessage());
      }
    }

    if (testStructure != null) {
      try {
        ddmStructureLocalService.deleteStructure(testStructure);
      } catch (Exception e) {
        System.err.println("Failed to clean up test structure: " + e.getMessage());
      }
    }
  }

  private static <T> T getService(Class<T> serviceClass) {
    var reference = bundleContext.getServiceReference(serviceClass);
    if (reference == null) {
      throw new RuntimeException("Service not found: " + serviceClass.getName());
    }
    return bundleContext.getService(reference);
  }

  @Test
  public void shouldIndexBasicArticleContent() throws Exception {


    var classNameId = PortalUtil.getClassNameId(JournalArticle.class.getName());
    var serviceContext =
        ServiceContextTestUtil.getServiceContext(TEST_GROUP_ID, TestPropsValues.getUserId());
    serviceContext.setAddGroupPermissions(true);
    serviceContext.setAddGuestPermissions(true);

    var structureKey = "TEST_STRUCTURE_" + System.currentTimeMillis();
    var structureDefinition =
        "<?xml version=\"1.0\"?>" +
        "<root available-locales=\"en_US\" default-locale=\"en_US\">" +
        "  <dynamic-element dataType=\"string\" " +
        "    name=\"content\" readOnly=\"false\" " +
        "    repeatable=\"false\" required=\"false\" " +
        "    showLabel=\"true\" type=\"text\">" +
        "    <meta-data locale=\"en_US\">" +
        "      <entry name=\"label\"><![CDATA[Content]]></entry>" +
        "    </meta-data>" +
        "  </dynamic-element>" +
        "</root>";

    var nameMap = new HashMap<Locale, String>();
    nameMap.put(Locale.US, "Test Article Structure");

    testStructure = ddmStructureLocalService.addStructure(
        null,
        TestPropsValues.getUserId(),
        TEST_GROUP_ID,
        0,
        classNameId,
        structureKey,
        nameMap,
        null,
        structureDefinition,
        "xml",
        serviceContext);



    var titleMap = new HashMap<Locale, String>();
    titleMap.put(Locale.US, "Test Searchable Article");
    titleMap.put(LocaleUtil.fromLanguageId("fi_FI"), "Testi hakukelpoinen artikkeli");

    var articleContent =
        "<?xml version=\"1.0\"?>" +
        "<root available-locales=\"en_US\" default-locale=\"en_US\">" +
        "  <dynamic-element name=\"content\" type=\"text\">" +
        "    <dynamic-content language-id=\"en_US\">" +
        "      <![CDATA[This is searchable test content]]>" +
        "    </dynamic-content>" +
        "  </dynamic-element>" +
        "</root>";

    testArticle = journalArticleLocalService.addArticle(
        null,
        TestPropsValues.getUserId(),
        TEST_GROUP_ID,
        0,
        titleMap,
        null,
        articleContent,
        testStructure.getStructureId(),
        null,
        serviceContext);



    // Reindex and wait for completion
    var indexer = indexerRegistry.getIndexer(JournalArticle.class);
    indexer.reindex(testArticle);


    // Wait for indexing to complete
    Thread.sleep(2000);

    // Search for the article to verify it's indexed
    var searchContext = new SearchContext();
    searchContext.setCompanyId(TestPropsValues.getCompanyId());
    searchContext.setGroupIds(new long[]{TEST_GROUP_ID});
    searchContext.setKeywords("searchable");

    var hits = indexer.search(searchContext);
    Assert.assertTrue("Should find at least one article", hits.getLength() > 0);

    var document = hits.doc(0);
    Assert.assertNotNull("Indexed document should not be null", document);

    var titleField = document.get("title_en_US");

    Assert.assertNotNull("Title field should not be null", titleField);
    Assert.assertTrue("Title should contain 'Test Searchable Article'",
        titleField.toLowerCase().contains("test searchable"));



  }

  @Test
  public void shouldHaveIndexerPostProcessorRegistered() {


    ServiceReference<?>[] references = null;
    try {
      references = bundleContext.getAllServiceReferences(
          IndexerPostProcessor.class.getName(),
          "(indexer.class.name=com.liferay.journal.model.JournalArticle)"
      );
    } catch (Exception e) {
      Assert.fail("Failed to get IndexerPostProcessor references: " + e.getMessage());
    }

    Assert.assertNotNull("Should have IndexerPostProcessor services", references);
    Assert.assertTrue("Should have at least one IndexerPostProcessor for JournalArticle",
        references.length > 0);

    var found = false;
    for (ServiceReference<?> ref : references) {
      var service = bundleContext.getService(ref);
      if (service instanceof JodJournalArticleIndexerPostProcessor) {
        found = true;

        bundleContext.ungetService(ref);
        break;
      }
      bundleContext.ungetService(ref);
    }

    Assert.assertTrue("JodJournalArticleIndexerPostProcessor should be registered", found);

  }

  @Test
  public void shouldHaveIndexerRegistryAvailable() {


    var reference =
        bundleContext.getServiceReference(IndexerRegistry.class);

    Assert.assertNotNull("IndexerRegistry service reference should not be null", reference);

    var indexerRegistry = bundleContext.getService(reference);
    Assert.assertNotNull("IndexerRegistry service should not be null", indexerRegistry);

    var indexer = indexerRegistry.getIndexer(JournalArticle.class);
    Assert.assertNotNull("JournalArticle indexer should be available", indexer);



    bundleContext.ungetService(reference);
  }

  @Test
  public void shouldVerifyPostProcessorConfiguration() {


    ServiceReference<?>[] references = null;
    try {
      references = bundleContext.getAllServiceReferences(
          IndexerPostProcessor.class.getName(),
          "(indexer.class.name=com.liferay.journal.model.JournalArticle)"
      );
    } catch (Exception e) {
      Assert.fail("Failed to get IndexerPostProcessor references: " + e.getMessage());
    }

    for (ServiceReference<?> ref : references) {
      var service = bundleContext.getService(ref);
      if (service instanceof JodJournalArticleIndexerPostProcessor) {
        var indexerClassName = ref.getProperty("indexer.class.name");
        Assert.assertEquals("Should be configured for JournalArticle",
            "com.liferay.journal.model.JournalArticle",
            indexerClassName);





        bundleContext.ungetService(ref);
        return;
      }
      bundleContext.ungetService(ref);
    }

    Assert.fail("JodJournalArticleIndexerPostProcessor not found");
  }

  @Test
  public void shouldIndexPDFAttachmentContent() throws Exception {


    // This test verifies complete PDF indexing workflow:
    // 1. Creates DDMStructure programmatically with document_library field
    // 2. Creates PDF FileEntry with unique searchable content
    // 3. Creates JournalArticle using DDMFormValues (not XML) that references the PDF
    // 4. Verifies PDF text is extracted and indexed via JodJournalArticleIndexerPostProcessor

    var serviceContext =
        ServiceContextTestUtil.getServiceContext(TEST_GROUP_ID, TestPropsValues.getUserId());
    serviceContext.setAddGroupPermissions(true);
    serviceContext.setAddGuestPermissions(true);

    // 1. Create DDMStructure with document_library field programmatically
    testStructure = createStructureWithDocumentLibraryField(serviceContext);


    // 2. Create PDF with unique searchable keyword
    var uniqueKeyword = "SEARCHABLE_PDF_" + System.currentTimeMillis();
    var pdfBytes = createSimplePDF("Test PDF document containing: " + uniqueKeyword);

    var dlAppRef =
        bundleContext.getServiceReference(DLAppLocalService.class);
    var dlAppLocalService =
        bundleContext.getService(dlAppRef);

    FileEntry fileEntry;
    try {
      fileEntry = dlAppLocalService.addFileEntry(
          null,
          TestPropsValues.getUserId(),
          TEST_GROUP_ID,
          0,
          "test-searchable.pdf",
          "application/pdf",
          pdfBytes,
          null,
          null,
          null,
          serviceContext);

    } finally {
      bundleContext.ungetService(dlAppRef);
    }

    // 3. Create article content using DDMFormValues
    var articleContent = createArticleContentWithPDFReference(testStructure, fileEntry);

    var titleMap = new HashMap<Locale, String>();
    titleMap.put(Locale.US, "Article with PDF " + uniqueKeyword);

    testArticle = journalArticleLocalService.addArticle(
        null,
        TestPropsValues.getUserId(),
        TEST_GROUP_ID,
        0,
        titleMap,
        null,
        articleContent,
        testStructure.getStructureId(),
        null,
        serviceContext);

    // 4. Reindex and wait for Elasticsearch
    var indexer = indexerRegistry.getIndexer(JournalArticle.class);
    indexer.reindex(testArticle);
    Thread.sleep(3000); // Wait for async indexing

    // 5. Search for the unique PDF keyword
    var searchContext = new SearchContext();
    searchContext.setCompanyId(TestPropsValues.getCompanyId());
    searchContext.setGroupIds(new long[]{TEST_GROUP_ID});
    searchContext.setKeywords(uniqueKeyword);

    var hits = indexer.search(searchContext);

    Assert.assertTrue("Should find article by PDF content keyword", hits.getLength() > 0);

    // 6. Verify it's our JournalArticle with indexed PDF content
    var foundArticle = false;
    for (int i = 0; i < hits.getLength(); i++) {
      var doc = hits.doc(i);
      var entryClassName = doc.get(Field.ENTRY_CLASS_NAME);

      if (JournalArticle.class.getName().equals(entryClassName)) {
        var articleId = doc.get("articleId");
        if (testArticle.getArticleId().equals(articleId)) {
          foundArticle = true;

          // Verify PDF content is in localized content field
          var localizedContent = doc.get("content_en_US");
          var content = doc.get(Field.CONTENT);

          var pdfIndexed = (localizedContent != null && localizedContent.contains(uniqueKeyword)) ||
                              (content != null && content.contains(uniqueKeyword));

          Assert.assertTrue(
              "PDF keyword '" + uniqueKeyword + "' should be in indexed content field",
              pdfIndexed);
          break;
        }
      }
    }

    Assert.assertTrue("Should find the specific article with indexed PDF", foundArticle);
  }

  /**
   * Creates a DDMStructure with a document_library field programmatically
   */
  private DDMStructure createStructureWithDocumentLibraryField(ServiceContext serviceContext)
      throws Exception {
    var classNameId = PortalUtil.getClassNameId(JournalArticle.class.getName());
    var structureKey = "TEST_PDF_STRUCTURE_" + System.currentTimeMillis();

    var ddmForm = getDdmForm();

    // Create layout with both fields
    var layout = new DDMFormLayout();
    layout.setDefaultLocale(Locale.US);
    layout.setPaginationMode("single-page");

    var page = new DDMFormLayoutPage();
    var row1 = new DDMFormLayoutRow();
    var column1 = new DDMFormLayoutColumn(12, "content");
    row1.setDDMFormLayoutColumns(Collections.singletonList(column1));
    var row2 = new DDMFormLayoutRow();
    var column2 = new DDMFormLayoutColumn(12, "pdfAttachment");
    row2.setDDMFormLayoutColumns(Collections.singletonList(column2));
    page.setDDMFormLayoutRows(Arrays.asList(row1, row2));
    layout.setDDMFormLayoutPages(Collections.singletonList(page));

    var nameMap = new HashMap<Locale, String>();
    nameMap.put(Locale.US, "Test PDF Structure");

    return ddmStructureLocalService.addStructure(
        null,
        TestPropsValues.getUserId(),
        TEST_GROUP_ID,
        0,
        classNameId,
        structureKey,
        nameMap,
        null,
        ddmForm,
        layout,
        "json",
        0,
        serviceContext);
  }

  private static DDMForm getDdmForm() {
    var ddmForm = new DDMForm();
    ddmForm.setAvailableLocales(Set.of(Locale.US));
    ddmForm.setDefaultLocale(Locale.US);

    // Add content field (required for postProcessor to extract PDF text into)
    var contentField = new DDMFormField("content", "rich_text");
    contentField.setDataType("html");
    contentField.setLocalizable(true);
    contentField.setRequired(false);
    contentField.setIndexType("text");
    var contentLabel = new LocalizedValue(Locale.US);
    contentLabel.addString(Locale.US, "Content");
    contentField.setLabel(contentLabel);
    contentField.setProperty("fieldReference", "content");
    ddmForm.addDDMFormField(contentField);

    // Add document_library field
    var pdfField = new DDMFormField(
        "pdfAttachment", "document_library");
    pdfField.setDataType("document-library");
    pdfField.setLocalizable(true);
    pdfField.setRequired(false);
    pdfField.setIndexType("keyword");
    var pdfLabel = new LocalizedValue(Locale.US);
    pdfLabel.addString(Locale.US, "PDF Attachment");
    pdfField.setLabel(pdfLabel);
    pdfField.setProperty("fieldReference", "pdfAttachment");
    ddmForm.addDDMFormField(pdfField);
    return ddmForm;
  }

  /**
   * Creates article content XML with PDF reference using DDMFormValues and JournalConverter
   */
  private String createArticleContentWithPDFReference(
      DDMStructure structure, FileEntry fileEntry)
      throws Exception {

    // Get DDM and JournalConverter services
    var ddmRef =
        bundleContext.getServiceReference(DDM.class);
    var ddm = bundleContext.getService(ddmRef);

    var converterRef =
        bundleContext.getServiceReference(JournalConverter.class);
    var converter = bundleContext.getService(converterRef);

    try {
      // Create DDMFormValues
      var ddmForm = structure.getDDMForm();
      var formValues = new DDMFormValues(ddmForm);
      formValues.setAvailableLocales(Set.of(Locale.US));
      formValues.setDefaultLocale(Locale.US);

      // Create content field value (some basic HTML content)
      var contentFieldValue = new DDMFormFieldValue();
      contentFieldValue.setName("content");
      var contentValue = new LocalizedValue(Locale.US);
      contentValue.addString(Locale.US, "<p>Article content goes here</p>");
      contentFieldValue.setValue(contentValue);
      formValues.addDDMFormFieldValue(contentFieldValue);

      // Create PDF field value with FileEntry reference
      var pdfFieldValue = getPdfFieldValue(fileEntry);

      formValues.addDDMFormFieldValue(pdfFieldValue);

      // Convert to XML content
      var fields = ddm.getFields(structure.getStructureId(), formValues);
      return converter.getContent(structure, fields, TEST_GROUP_ID);

    } finally {
      bundleContext.ungetService(ddmRef);
      bundleContext.ungetService(converterRef);
    }
  }

  private static DDMFormFieldValue getPdfFieldValue(FileEntry fileEntry) {
    var pdfFieldValue = new DDMFormFieldValue();
    pdfFieldValue.setName("pdfAttachment");

    // Format: JSON with fileEntryId, groupId, uuid, and version
    var pdfReference = String.format(
        "{\"fileEntryId\":%d,\"groupId\":%d,\"uuid\":\"%s\",\"version\":\"%s\"}",
        fileEntry.getFileEntryId(),
        fileEntry.getGroupId(),
        fileEntry.getUuid(),
        fileEntry.getVersion());

    // document_library field uses LocalizedValue with JSON reference
    var pdfValue = new LocalizedValue(Locale.US);
    pdfValue.addString(Locale.US, pdfReference);
    pdfFieldValue.setValue(pdfValue);
    return pdfFieldValue;
  }

  /**
   * Creates a simple PDF with the given text content
   */
  private byte[] createSimplePDF(String textContent) throws Exception {
    try (var baos = new ByteArrayOutputStream()) {
      var document = new PDDocument();
      var page = new PDPage();
      document.addPage(page);

      var contentStream =
          new PDPageContentStream(document, page);

      contentStream.beginText();
      contentStream.setFont(PDType1Font.HELVETICA, 12);
      contentStream.newLineAtOffset(100, 700);
      contentStream.showText(textContent);
      contentStream.endText();
      contentStream.close();

      document.save(baos);
      document.close();

      return baos.toByteArray();
    }
  }
}
