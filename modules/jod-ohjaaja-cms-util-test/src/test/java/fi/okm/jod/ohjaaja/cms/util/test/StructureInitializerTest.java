/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.util.test;

import static fi.okm.jod.ohjaaja.cms.util.StructureInitializer.ARTICLE_STRUCTURE_EXTERNAL_REFERENCE_CODE;
import static fi.okm.jod.ohjaaja.cms.util.StructureInitializer.LINK_FIELDSET_EXTERNAL_REFERENCE_CODE;
import static fi.okm.jod.ohjaaja.cms.util.StructureInitializer.NEWS_FOLDER_EXTERNAL_REFERENCE_CODE;
import static fi.okm.jod.ohjaaja.cms.util.StructureInitializer.NEWS_STRUCTURE_EXTERNAL_REFERENCE_CODE;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.data.engine.rest.dto.v2_0.DataDefinition;
import com.liferay.data.engine.rest.resource.v2_0.DataDefinitionResource;
import com.liferay.dynamic.data.mapping.constants.DDMStructureConstants;
import com.liferay.dynamic.data.mapping.exception.NoSuchStructureException;
import com.liferay.dynamic.data.mapping.model.DDMStructure;
import com.liferay.dynamic.data.mapping.service.DDMStructureLocalService;
import com.liferay.headless.delivery.dto.v1_0.ContentField;
import com.liferay.headless.delivery.dto.v1_0.StructuredContent;
import com.liferay.headless.delivery.resource.v1_0.StructuredContentResource;
import com.liferay.journal.constants.JournalFolderConstants;
import com.liferay.journal.exception.InvalidDDMStructureException;
import com.liferay.journal.model.JournalFolder;
import com.liferay.journal.service.JournalArticleLocalService;
import com.liferay.journal.service.JournalFolderLocalService;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.portal.test.log.LoggerTestUtil;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import fi.okm.jod.ohjaaja.cms.util.JodOhjaajaCmsUtil;
import fi.okm.jod.ohjaaja.cms.util.StructureInitializer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Integration tests for StructureInitializer. Each test starts from the structures created from the
 * definitions, as in an empty environment.
 */
@RunWith(Arquillian.class)
public class StructureInitializerTest {

  @ClassRule @Rule
  public static final LiferayIntegrationTestRule liferayIntegrationTestRule =
      new LiferayIntegrationTestRule();

  // Created by hand on the servers before the structures were managed in code
  private static final String LEGACY_ARTICLE_EXTERNAL_REFERENCE_CODE =
      "2bfe79e6-f496-914a-684e-57719a295a82";
  private static final String LEGACY_FIELDSET_EXTERNAL_REFERENCE_CODE =
      "262bb93a-70eb-7f8c-65d7-aa29aa7f2d1d";

  private static final String INGRESS_FIELD = "Text06331573";

  @Inject private StructureInitializer structureInitializer;
  @Inject private DDMStructureLocalService ddmStructureLocalService;
  @Inject private JodOhjaajaCmsUtil jodOhjaajaCmsUtil;
  @Inject private DataDefinitionResource.Factory dataDefinitionResourceFactory;
  @Inject private StructuredContentResource.Factory structuredContentResourceFactory;
  @Inject private UserLocalService userLocalService;
  @Inject private JournalArticleLocalService journalArticleLocalService;
  @Inject private JournalFolderLocalService journalFolderLocalService;

  private DataDefinitionResource dataDefinitionResource;
  private StructuredContentResource structuredContentResource;
  private long groupId;
  private final List<Long> structuredContentIds = new ArrayList<>();

  @Before
  public void setUp() throws Exception {
    groupId = jodOhjaajaCmsUtil.getJodOhjaajaCmsGroup().getGroupId();
    dataDefinitionResource =
        dataDefinitionResourceFactory
            .create()
            .user(TestPropsValues.getUser())
            .checkPermissions(false)
            .build();
    structuredContentResource =
        structuredContentResourceFactory
            .create()
            .user(TestPropsValues.getUser())
            // The default language of the article structure
            .preferredLocale(LocaleUtil.fromLanguageId("en_US"))
            .checkPermissions(false)
            .build();

    deleteStructures();
    structureInitializer.initStructures();
  }

  @After
  public void tearDown() throws Exception {
    for (var structuredContentId : structuredContentIds) {
      structuredContentResource.deleteStructuredContent(structuredContentId);
    }
    // Leaves the structures as created from the definitions for the other tests
    deleteStructures();
    structureInitializer.initStructures();
  }

  @Test
  public void shouldCreateStructuresInEmptyEnvironment() throws Exception {
    var fieldset = getStructure(LINK_FIELDSET_EXTERNAL_REFERENCE_CODE);
    var article = getStructure(ARTICLE_STRUCTURE_EXTERNAL_REFERENCE_CODE);

    var fieldsetField = getFieldsetField(getDefinition(article));
    Assert.assertEquals(
        String.valueOf(fieldset.getStructureId()),
        fieldsetField.getJSONObject("customProperties").getString("ddmStructureId"));
    Assert.assertEquals(
        List.of(
            "Text06331573",
            "RichText28658582",
            "Image59796823",
            "DocumentLibrary22581947",
            "Fieldset73641796"),
        getFieldNames(getDefinition(article).getJSONArray("dataDefinitionFields")));

    var content = addArticle(article.getStructureId());
    assertArticleContent(content.getId());

    assertNotUpdatedOnRestart(fieldset, article);
  }

  @Test
  public void shouldTakeOverProductionStructuresAndKeepTheArticles() throws Exception {
    // As on the servers: the structures created by hand, with articles. The fieldset could not be
    // exported from production, so it is the one of the definition.
    var fieldsetDefinition = getDefinition(getStructure(LINK_FIELDSET_EXTERNAL_REFERENCE_CODE));
    fieldsetDefinition.remove("id");
    deleteStructures();
    var fieldset =
        dataDefinitionResource.postSiteDataDefinitionByContentType(
            groupId,
            "journal",
            DataDefinition.toDTO(
                fieldsetDefinition
                    .put("externalReferenceCode", LEGACY_FIELDSET_EXTERNAL_REFERENCE_CODE)
                    .toString()));
    var articleDefinition = JSONFactoryUtil.createJSONObject(ProdArticleStructure.JSON);
    var customProperties = getFieldsetField(articleDefinition).getJSONObject("customProperties");
    customProperties.put("ddmStructureId", String.valueOf(fieldset.getId()));
    customProperties.put("ddmStructureKey", fieldset.getDataDefinitionKey());
    var article =
        dataDefinitionResource.postSiteDataDefinitionByContentType(
            groupId,
            "journal",
            DataDefinition.toDTO(
                articleDefinition
                    .put("externalReferenceCode", LEGACY_ARTICLE_EXTERNAL_REFERENCE_CODE)
                    .toString()));
    var content = addArticle(article.getId());
    var fieldsetVersion = getVersion(fieldset.getId());

    structureInitializer.initStructures();

    var takenOverFieldset = getStructure(LINK_FIELDSET_EXTERNAL_REFERENCE_CODE);
    var takenOverArticle = getStructure(ARTICLE_STRUCTURE_EXTERNAL_REFERENCE_CODE);
    Assert.assertEquals(fieldset.getId().longValue(), takenOverFieldset.getStructureId());
    Assert.assertEquals(article.getId().longValue(), takenOverArticle.getStructureId());
    // The fieldset is as defined, only its external reference code changed
    Assert.assertEquals(fieldsetVersion, takenOverFieldset.getVersion());
    Assert.assertEquals(
        LINK_FIELDSET_EXTERNAL_REFERENCE_CODE,
        getFieldsetField(getDefinition(takenOverArticle))
            .getJSONObject("customProperties")
            .getString("externalReferenceCode"));
    assertArticleContent(content.getId());

    assertNotUpdatedOnRestart(takenOverFieldset, takenOverArticle);
  }

  @Test
  public void shouldTakeOverArticleStructureByName() throws Exception {
    // As in an environment created separately, where the structure has another external
    // reference code but has been given the name of the definition
    var article = getStructure(ARTICLE_STRUCTURE_EXTERNAL_REFERENCE_CODE);
    var content = addArticle(article.getStructureId());
    article.setExternalReferenceCode("hand-made-" + System.currentTimeMillis());
    ddmStructureLocalService.updateDDMStructure(article);

    structureInitializer.initStructures();

    var takenOver = getStructure(ARTICLE_STRUCTURE_EXTERNAL_REFERENCE_CODE);
    Assert.assertEquals(article.getStructureId(), takenOver.getStructureId());
    Assert.assertEquals("Ohjaaja CMS Artikkeli", takenOver.getName("fi_FI"));
    assertArticleContent(content.getId());
  }

  @Test
  public void shouldTakeOverArticleStructureByItsFormerName() throws Exception {
    // As in an environment created separately, before the structure was renamed
    var article = getStructure(ARTICLE_STRUCTURE_EXTERNAL_REFERENCE_CODE);
    var content = addArticle(article.getStructureId());
    article.setExternalReferenceCode("hand-made-" + System.currentTimeMillis());
    article.setNameMap(
        Map.of(
            LocaleUtil.fromLanguageId("fi_FI"), "Artikkeli",
            LocaleUtil.fromLanguageId("en_US"), "Article"));
    ddmStructureLocalService.updateDDMStructure(article);

    structureInitializer.initStructures();

    var takenOver = getStructure(ARTICLE_STRUCTURE_EXTERNAL_REFERENCE_CODE);
    Assert.assertEquals(article.getStructureId(), takenOver.getStructureId());
    // Renamed as defined
    Assert.assertEquals("Ohjaaja CMS Artikkeli", takenOver.getName("fi_FI"));
    assertArticleContent(content.getId());
  }

  @Test
  public void shouldRestoreStructureChangedInTheUi() throws Exception {
    var article = getStructure(ARTICLE_STRUCTURE_EXTERNAL_REFERENCE_CODE);
    var content = addArticle(article.getStructureId());
    var definition = getDefinition(article);
    getField(definition, INGRESS_FIELD).getJSONObject("label").put("fi_FI", "Muutettu");
    dataDefinitionResource.putDataDefinition(
        article.getStructureId(), DataDefinition.toDTO(definition.toString()));

    structureInitializer.initStructures();

    var restored = getStructure(ARTICLE_STRUCTURE_EXTERNAL_REFERENCE_CODE);
    Assert.assertEquals(
        "Tiivistelmä",
        getField(getDefinition(restored), INGRESS_FIELD).getJSONObject("label").getString("fi_FI"));
    assertArticleContent(content.getId());

    assertNotUpdatedOnRestart(getStructure(LINK_FIELDSET_EXTERNAL_REFERENCE_CODE), restored);
  }

  @Test
  public void shouldNotUpdateStructureIfFieldsWouldBeLost() throws Exception {
    var article = getStructure(ARTICLE_STRUCTURE_EXTERNAL_REFERENCE_CODE);
    var definition = getDefinition(article);
    // A field added in the UI, which may already have content
    var addedField =
        JSONFactoryUtil.createJSONObject(getField(definition, INGRESS_FIELD).toString());
    addedField.put("name", "Text99999999");
    addedField.put("required", false);
    addedField.getJSONObject("customProperties").put("fieldReference", "addedInUi");
    definition.getJSONArray("dataDefinitionFields").put(addedField);
    definition
        .getJSONObject("defaultDataLayout")
        .getJSONArray("dataLayoutPages")
        .getJSONObject(0)
        .getJSONArray("dataLayoutRows")
        .put(
            JSONFactoryUtil.createJSONObject(
                "{\"dataLayoutColumns\": [{\"columnSize\": 12, \"fieldNames\": [\"Text99999999\"]}]}"));
    dataDefinitionResource.putDataDefinition(
        article.getStructureId(), DataDefinition.toDTO(definition.toString()));

    try (var logCapture =
        LoggerTestUtil.configureLog4JLogger(
            "fi.okm.jod.ohjaaja.cms.util.StructureInitializerImpl", LoggerTestUtil.WARN)) {
      structureInitializer.initStructures();

      Assert.assertEquals(
          List.of(
              "Not updating structure jod-ohjaaja-cms-article: the definition would remove or"
                  + " change the type of fields [Text99999999], which would lose the content of"
                  + " the existing articles"),
          logCapture.getMessages());
    }
    Assert.assertTrue(
        getFieldNames(
                getDefinition(getStructure(ARTICLE_STRUCTURE_EXTERNAL_REFERENCE_CODE))
                    .getJSONArray("dataDefinitionFields"))
            .contains("Text99999999"));
  }

  @Test
  public void shouldCreateNewsStructureWithTheFieldsOfTheArticle() throws Exception {
    var fieldset = getStructure(LINK_FIELDSET_EXTERNAL_REFERENCE_CODE);
    var article = getDefinition(getStructure(ARTICLE_STRUCTURE_EXTERNAL_REFERENCE_CODE));
    var news = getDefinition(getStructure(NEWS_STRUCTURE_EXTERNAL_REFERENCE_CODE));

    Assert.assertEquals("Ohjaaja CMS Uutinen", news.getJSONObject("name").getString("fi_FI"));
    Assert.assertEquals(
        String.valueOf(fieldset.getStructureId()),
        getFieldsetField(news).getJSONObject("customProperties").getString("ddmStructureId"));
    var newsFields = news.getJSONArray("dataDefinitionFields");
    Assert.assertEquals(
        List.of("Text06331573", "RichText28658582", "Image59796823", "Fieldset73641796"),
        getFieldNames(newsFields));
    // The same names and field references as in the article, so that the UI can render both alike
    for (var name : getFieldNames(newsFields)) {
      var newsField = getField(news, name);
      var articleField = getField(article, name);
      Assert.assertEquals(
          name, articleField.getString("fieldType"), newsField.getString("fieldType"));
      Assert.assertEquals(
          name,
          articleField.getJSONObject("customProperties").getString("fieldReference"),
          newsField.getJSONObject("customProperties").getString("fieldReference"));
    }
  }

  @Test
  public void shouldCreateNewsFolderAllowingOnlyNews() throws Exception {
    var folder = getNewsFolder();
    var news = getStructure(NEWS_STRUCTURE_EXTERNAL_REFERENCE_CODE);
    Assert.assertEquals("Ajankohtaista", folder.getName());
    Assert.assertEquals(
        JournalFolderConstants.RESTRICTION_TYPE_DDM_STRUCTURES_AND_WORKFLOW,
        folder.getRestrictionType());
    Assert.assertEquals(List.of(news.getStructureId()), getAllowedStructureIds(folder));

    addNews(news.getStructureId(), List.of("en-US", "fi-FI"));
    try {
      addArticle(getStructure(ARTICLE_STRUCTURE_EXTERNAL_REFERENCE_CODE).getStructureId(), folder);
      Assert.fail("An article was added to the news folder");
    } catch (InvalidDDMStructureException e) {
      // Expected
    }

    try (var logCapture =
        LoggerTestUtil.configureLog4JLogger(
            "fi.okm.jod.ohjaaja.cms.util.StructureInitializerImpl", LoggerTestUtil.INFO)) {
      structureInitializer.initStructures();

      Assert.assertTrue(
          String.valueOf(logCapture.getMessages()),
          logCapture
              .getMessages()
              .contains("Folder " + NEWS_FOLDER_EXTERNAL_REFERENCE_CODE + " is up to date"));
    }
  }

  @Test
  public void shouldRestoreNewsFolderChangedInTheUi() throws Exception {
    var folder = getNewsFolder();
    var serviceContext = new ServiceContext();
    serviceContext.setScopeGroupId(groupId);
    journalFolderLocalService.updateFolder(
        TestPropsValues.getUserId(),
        groupId,
        folder.getFolderId(),
        folder.getParentFolderId(),
        "Muutettu",
        folder.getDescription(),
        new long[0],
        JournalFolderConstants.RESTRICTION_TYPE_INHERIT,
        false,
        serviceContext);

    structureInitializer.initStructures();

    var restored = getNewsFolder();
    Assert.assertEquals(folder.getFolderId(), restored.getFolderId());
    Assert.assertEquals("Ajankohtaista", restored.getName());
    Assert.assertEquals(
        JournalFolderConstants.RESTRICTION_TYPE_DDM_STRUCTURES_AND_WORKFLOW,
        restored.getRestrictionType());
    Assert.assertEquals(
        List.of(getStructure(NEWS_STRUCTURE_EXTERNAL_REFERENCE_CODE).getStructureId()),
        getAllowedStructureIds(restored));
  }

  @Test
  public void shouldRestoreNewsFolderAllowingAlsoOtherStructures() throws Exception {
    // The restriction type is kept, but another structure is allowed too and the description
    // changed, e.g. to add articles to the folder in the UI
    var folder = getNewsFolder();
    var newsStructureId = getStructure(NEWS_STRUCTURE_EXTERNAL_REFERENCE_CODE).getStructureId();
    var serviceContext = new ServiceContext();
    serviceContext.setScopeGroupId(groupId);
    journalFolderLocalService.updateFolder(
        TestPropsValues.getUserId(),
        groupId,
        folder.getFolderId(),
        folder.getParentFolderId(),
        folder.getName(),
        "Muutettu",
        new long[] {
          newsStructureId, getStructure(ARTICLE_STRUCTURE_EXTERNAL_REFERENCE_CODE).getStructureId()
        },
        JournalFolderConstants.RESTRICTION_TYPE_DDM_STRUCTURES_AND_WORKFLOW,
        false,
        serviceContext);
    Assert.assertEquals(2, getAllowedStructureIds(getNewsFolder()).size());

    structureInitializer.initStructures();

    var restored = getNewsFolder();
    Assert.assertEquals(folder.getFolderId(), restored.getFolderId());
    Assert.assertEquals(folder.getDescription(), restored.getDescription());
    Assert.assertEquals(List.of(newsStructureId), getAllowedStructureIds(restored));
  }

  @Test
  public void shouldRestoreNewsFolderFromTheTrash() throws Exception {
    var folder = getNewsFolder();
    var added =
        addNews(
            getStructure(NEWS_STRUCTURE_EXTERNAL_REFERENCE_CODE).getStructureId(),
            List.of("en-US", "fi-FI"));
    journalFolderLocalService.moveFolderToTrash(TestPropsValues.getUserId(), folder.getFolderId());
    Assert.assertTrue(getNewsFolder().isInTrash());
    var trashed = journalArticleLocalService.getLatestArticle(added.getId());
    // Liferay moves the articles of a trashed folder to the trash with it
    Assert.assertTrue(trashed.isInTrash());

    structureInitializer.initStructures();

    var restored = getNewsFolder();
    Assert.assertEquals(folder.getFolderId(), restored.getFolderId());
    Assert.assertFalse(restored.isInTrash());
    Assert.assertEquals("Ajankohtaista", restored.getName());
    // The news are restored with their folder
    var article = journalArticleLocalService.getLatestArticle(added.getId());
    Assert.assertFalse(article.isInTrash());
    Assert.assertEquals(WorkflowConstants.STATUS_APPROVED, article.getStatus());
  }

  @Test
  public void shouldServePublishedNewsToGuestsInEveryLanguage() throws Exception {
    var news = getStructure(NEWS_STRUCTURE_EXTERNAL_REFERENCE_CODE);
    var added = addNews(news.getStructureId(), List.of("en-US", "fi-FI", "sv-SE"));

    for (var languageId : List.of("en-US", "fi-FI", "sv-SE")) {
      var content = getAsGuest(added.getId(), languageId);
      Assert.assertEquals(news.getStructureId(), content.getContentStructureId().longValue());
      Assert.assertEquals("Uutinen " + languageId, content.getTitle());
      Assert.assertEquals("Ingressi " + languageId, getData(content.getContentFields(), "ingress"));
      Assert.assertEquals(
          "<p>Sisältö " + languageId + "</p>", getData(content.getContentFields(), "content"));
      var link = findContentField(content.getContentFields(), "link");
      Assert.assertEquals(
          "Linkki " + languageId, getData(link.getNestedContentFields(), "linktext"));
      Assert.assertEquals(
          "https://example.org/" + languageId, getData(link.getNestedContentFields(), "linkurl"));
    }
  }

  private void assertNotUpdatedOnRestart(DDMStructure fieldset, DDMStructure article)
      throws Exception {
    var news = getStructure(NEWS_STRUCTURE_EXTERNAL_REFERENCE_CODE);
    // Only a fragment is listed among the fieldsets and not among the structures
    Assert.assertEquals(
        DDMStructureConstants.TYPE_FRAGMENT,
        ddmStructureLocalService.getStructure(fieldset.getStructureId()).getType());
    Assert.assertEquals(
        DDMStructureConstants.TYPE_DEFAULT,
        ddmStructureLocalService.getStructure(article.getStructureId()).getType());
    Assert.assertEquals(DDMStructureConstants.TYPE_DEFAULT, news.getType());

    var fieldsetVersion = getVersion(fieldset.getStructureId());
    var articleVersion = getVersion(article.getStructureId());
    var newsVersion = getVersion(news.getStructureId());

    // Updating a structure does not necessarily change its version, so the log tells whether
    // anything was written
    try (var logCapture =
        LoggerTestUtil.configureLog4JLogger(
            "fi.okm.jod.ohjaaja.cms.util.StructureInitializerImpl", LoggerTestUtil.INFO)) {
      structureInitializer.initStructures();

      Assert.assertEquals(
          List.of(
              "Structure " + LINK_FIELDSET_EXTERNAL_REFERENCE_CODE + " is up to date",
              "Structure " + ARTICLE_STRUCTURE_EXTERNAL_REFERENCE_CODE + " is up to date",
              "Structure " + NEWS_STRUCTURE_EXTERNAL_REFERENCE_CODE + " is up to date",
              "Folder " + NEWS_FOLDER_EXTERNAL_REFERENCE_CODE + " is up to date"),
          logCapture.getMessages());
    }

    Assert.assertEquals(fieldsetVersion, getVersion(fieldset.getStructureId()));
    Assert.assertEquals(articleVersion, getVersion(article.getStructureId()));
    Assert.assertEquals(newsVersion, getVersion(news.getStructureId()));
  }

  /**
   * A news item in the news folder, readable by anyone, translated to the given languages (e.g.
   * "fi-FI").
   */
  private StructuredContent addNews(long structureId, List<String> languageIds) throws Exception {
    var content =
        structuredContentResource.postStructuredContentFolderStructuredContent(
            getNewsFolder().getFolderId(),
            StructuredContent.toDTO(
                """
                {
                  "contentStructureId": %d,
                  "title": "Uutinen en-US",
                  "title_i18n": %s,
                  "viewableBy": "Anyone",
                  "contentFields": [
                    %s,
                    %s,
                    {"name": "link", "fieldReference": "link", "nestedContentFields": [%s, %s]}
                  ]
                }
                """
                    .formatted(
                        structureId,
                        localized(languageIds, "Uutinen %s"),
                        localizedContentField("ingress", languageIds, "Ingressi %s"),
                        localizedContentField("content", languageIds, "<p>Sisältö %s</p>"),
                        localizedContentField("linktext", languageIds, "Linkki %s"),
                        localizedContentField("linkurl", languageIds, "https://example.org/%s"))));
    structuredContentIds.add(content.getId());
    return content;
  }

  private static JSONObject localized(List<String> languageIds, String format) {
    var values = JSONFactoryUtil.createJSONObject();
    for (var languageId : languageIds) {
      values.put(languageId, format.formatted(languageId));
    }
    return values;
  }

  private static String localizedContentField(
      String name, List<String> languageIds, String format) {
    var values = JSONFactoryUtil.createJSONObject();
    for (var languageId : languageIds) {
      values.put(
          languageId, JSONFactoryUtil.createJSONObject().put("data", format.formatted(languageId)));
    }
    return JSONFactoryUtil.createJSONObject()
        .put("name", name)
        .put("fieldReference", name)
        .put("contentFieldValue", values.getJSONObject("en-US"))
        .put("contentFieldValue_i18n", values)
        .toString();
  }

  // As the UI reads it: an anonymous user, with permission checks
  private StructuredContent getAsGuest(long structuredContentId, String languageId)
      throws Exception {
    return structuredContentResourceFactory
        .create()
        .user(userLocalService.getGuestUser(TestPropsValues.getCompanyId()))
        .preferredLocale(Locale.forLanguageTag(languageId))
        .checkPermissions(true)
        .build()
        .getStructuredContent(structuredContentId);
  }

  private StructuredContent addArticle(long structureId) throws Exception {
    return addArticle(structureId, null);
  }

  private StructuredContent addArticle(long structureId, JournalFolder folder) throws Exception {
    var dto =
        StructuredContent.toDTO(
            """
                {
                  "contentStructureId": %d,
                  "title": "Rakennetesti",
                  "contentFields": [
                    %s,
                    %s,
                    {"name": "link", "fieldReference": "link", "nestedContentFields": [%s, %s]}
                  ]
                }
                """
                .formatted(
                    structureId,
                    contentField("ingress", "Tiivistelmän teksti"),
                    contentField("content", "<p>Sisällön teksti</p>"),
                    contentField("linktext", "Linkin kuvaus"),
                    contentField("linkurl", "https://example.org")));
    var content =
        folder == null
            ? structuredContentResource.postSiteStructuredContent(groupId, dto)
            : structuredContentResource.postStructuredContentFolderStructuredContent(
                folder.getFolderId(), dto);
    structuredContentIds.add(content.getId());
    return content;
  }

  private static String contentField(String name, String data) {
    var value = JSONFactoryUtil.createJSONObject().put("data", data);
    return JSONFactoryUtil.createJSONObject()
        .put("name", name)
        // Headless delivery matches the input to the fields by the field reference
        .put("fieldReference", name)
        .put("contentFieldValue", value)
        .put("contentFieldValue_i18n", JSONFactoryUtil.createJSONObject().put("en-US", value))
        .toString();
  }

  // The UI reads the fields by their field references through headless delivery
  private void assertArticleContent(long structuredContentId) throws Exception {
    var contentFields =
        structuredContentResource.getStructuredContent(structuredContentId).getContentFields();
    Assert.assertEquals("Tiivistelmän teksti", getData(contentFields, "ingress"));
    Assert.assertEquals("<p>Sisällön teksti</p>", getData(contentFields, "content"));
    var link = findContentField(contentFields, "link");
    Assert.assertEquals("Linkin kuvaus", getData(link.getNestedContentFields(), "linktext"));
    Assert.assertEquals("https://example.org", getData(link.getNestedContentFields(), "linkurl"));
  }

  private static String getData(ContentField[] contentFields, String name) {
    return findContentField(contentFields, name).getContentFieldValue().getData();
  }

  private static ContentField findContentField(ContentField[] contentFields, String name) {
    return Arrays.stream(contentFields)
        .filter(contentField -> name.equals(contentField.getFieldReference()))
        .findFirst()
        .orElseThrow(
            () ->
                new AssertionError(
                    "Content field "
                        + name
                        + " not found in "
                        + Arrays.stream(contentFields)
                            .map(
                                contentField ->
                                    contentField.getName() + "/" + contentField.getFieldReference())
                            .toList()));
  }

  private DDMStructure getStructure(String externalReferenceCode) throws Exception {
    var structure = fetchStructure(externalReferenceCode);
    Assert.assertNotNull("Structure " + externalReferenceCode + " not found", structure);
    return structure;
  }

  private DDMStructure fetchStructure(String externalReferenceCode) throws Exception {
    try {
      return ddmStructureLocalService.getStructure(
          dataDefinitionResource
              .getSiteDataDefinitionByContentTypeByExternalReferenceCode(
                  groupId, "journal", externalReferenceCode)
              .getId());
    } catch (NoSuchStructureException e) {
      return null;
    }
  }

  private JournalFolder getNewsFolder() {
    var folder =
        journalFolderLocalService.fetchJournalFolderByExternalReferenceCode(
            NEWS_FOLDER_EXTERNAL_REFERENCE_CODE, groupId);
    Assert.assertNotNull("Folder " + NEWS_FOLDER_EXTERNAL_REFERENCE_CODE + " not found", folder);
    return folder;
  }

  private List<Long> getAllowedStructureIds(JournalFolder folder) throws Exception {
    return journalFolderLocalService
        .getDDMStructures(
            new long[] {groupId},
            folder.getFolderId(),
            JournalFolderConstants.RESTRICTION_TYPE_DDM_STRUCTURES_AND_WORKFLOW)
        .stream()
        .map(DDMStructure::getStructureId)
        .toList();
  }

  private String getVersion(long structureId) throws Exception {
    return ddmStructureLocalService.getStructure(structureId).getVersion();
  }

  private JSONObject getDefinition(DDMStructure structure) throws Exception {
    return JSONFactoryUtil.createJSONObject(
        dataDefinitionResource.getDataDefinition(structure.getStructureId()).toString());
  }

  private static JSONObject getFieldsetField(JSONObject definition) {
    var fields = definition.getJSONArray("dataDefinitionFields");
    for (int i = 0; i < fields.length(); i++) {
      if ("fieldset".equals(fields.getJSONObject(i).getString("fieldType"))) {
        return fields.getJSONObject(i);
      }
    }
    throw new AssertionError("No fieldset field");
  }

  private static JSONObject getField(JSONObject definition, String name) {
    var fields = definition.getJSONArray("dataDefinitionFields");
    for (int i = 0; i < fields.length(); i++) {
      if (name.equals(fields.getJSONObject(i).getString("name"))) {
        return fields.getJSONObject(i);
      }
    }
    throw new AssertionError("No field " + name);
  }

  private static List<String> getFieldNames(JSONArray fields) {
    var names = new ArrayList<String>();
    for (int i = 0; i < fields.length(); i++) {
      names.add(fields.getJSONObject(i).getString("name"));
    }
    return names;
  }

  /**
   * Deletes the news folder, which refers to the news structure, and the managed structures by
   * their current or former external reference codes.
   */
  private void deleteStructures() throws Exception {
    var folder =
        journalFolderLocalService.fetchJournalFolderByExternalReferenceCode(
            NEWS_FOLDER_EXTERNAL_REFERENCE_CODE, groupId);
    if (folder != null) {
      journalFolderLocalService.deleteFolder(folder.getFolderId());
    }
    for (var externalReferenceCode :
        List.of(
            ARTICLE_STRUCTURE_EXTERNAL_REFERENCE_CODE,
            LEGACY_ARTICLE_EXTERNAL_REFERENCE_CODE,
            NEWS_STRUCTURE_EXTERNAL_REFERENCE_CODE,
            // Last, the structures above use it
            LINK_FIELDSET_EXTERNAL_REFERENCE_CODE,
            LEGACY_FIELDSET_EXTERNAL_REFERENCE_CODE)) {
      var structure = fetchStructure(externalReferenceCode);
      if (structure != null) {
        dataDefinitionResource.deleteDataDefinition(structure.getStructureId());
      }
    }
  }
}
