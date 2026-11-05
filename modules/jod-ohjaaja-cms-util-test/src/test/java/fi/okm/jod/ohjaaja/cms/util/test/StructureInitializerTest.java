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

import com.liferay.data.engine.rest.dto.v2_0.DataDefinition;
import com.liferay.data.engine.rest.resource.v2_0.DataDefinitionResource;
import com.liferay.dynamic.data.mapping.exception.NoSuchStructureException;
import com.liferay.dynamic.data.mapping.model.DDMStructure;
import com.liferay.dynamic.data.mapping.model.DDMStructureConstants;
import com.liferay.dynamic.data.mapping.service.DDMStructureLocalService;
import com.liferay.headless.delivery.dto.v1_0.ContentField;
import com.liferay.headless.delivery.dto.v1_0.StructuredContent;
import com.liferay.headless.delivery.resource.v1_0.StructuredContentResource;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.test.log.LoggerTestUtil;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import fi.okm.jod.ohjaaja.cms.testrunner.client.JodInContainerRunner;
import fi.okm.jod.ohjaaja.cms.util.JodOhjaajaCmsUtil;
import fi.okm.jod.ohjaaja.cms.util.StructureInitializer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.osgi.framework.FrameworkUtil;

/**
 * Integration tests for StructureInitializer. Each test starts from the structures created from the
 * definitions, as in an empty environment.
 */
@RunWith(JodInContainerRunner.class)
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

  private StructureInitializer structureInitializer;
  private DataDefinitionResource dataDefinitionResource;
  private StructuredContentResource structuredContentResource;
  private DDMStructureLocalService ddmStructureLocalService;
  private long groupId;
  private final List<Long> structuredContentIds = new ArrayList<>();

  @Before
  public void setUp() throws Exception {
    structureInitializer = getService(StructureInitializer.class);
    ddmStructureLocalService = getService(DDMStructureLocalService.class);
    groupId = getService(JodOhjaajaCmsUtil.class).getJodOhjaajaCmsGroup().getGroupId();
    dataDefinitionResource =
        getService(DataDefinitionResource.Factory.class)
            .create()
            .user(TestPropsValues.getUser())
            .checkPermissions(false)
            .build();
    structuredContentResource =
        getService(StructuredContentResource.Factory.class)
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
            "fi.okm.jod.ohjaaja.cms.util.StructureInitializerImpl", LoggerTestUtil.ERROR)) {
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

  private void assertNotUpdatedOnRestart(DDMStructure fieldset, DDMStructure article)
      throws Exception {
    // Only a fragment is listed among the fieldsets and not among the structures
    Assert.assertEquals(
        DDMStructureConstants.TYPE_FRAGMENT,
        ddmStructureLocalService.getStructure(fieldset.getStructureId()).getType());
    Assert.assertEquals(
        DDMStructureConstants.TYPE_DEFAULT,
        ddmStructureLocalService.getStructure(article.getStructureId()).getType());

    var fieldsetVersion = getVersion(fieldset.getStructureId());
    var articleVersion = getVersion(article.getStructureId());

    structureInitializer.initStructures();

    Assert.assertEquals(fieldsetVersion, getVersion(fieldset.getStructureId()));
    Assert.assertEquals(articleVersion, getVersion(article.getStructureId()));
  }

  private StructuredContent addArticle(long structureId) throws Exception {
    var content =
        structuredContentResource.postSiteStructuredContent(
            groupId,
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
                        contentField("linkurl", "https://example.org"))));
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

  /** Deletes the managed structures by their current or former external reference codes. */
  private void deleteStructures() throws Exception {
    for (var externalReferenceCode :
        List.of(
            ARTICLE_STRUCTURE_EXTERNAL_REFERENCE_CODE,
            LEGACY_ARTICLE_EXTERNAL_REFERENCE_CODE,
            LINK_FIELDSET_EXTERNAL_REFERENCE_CODE,
            LEGACY_FIELDSET_EXTERNAL_REFERENCE_CODE)) {
      var structure = fetchStructure(externalReferenceCode);
      if (structure != null) {
        dataDefinitionResource.deleteDataDefinition(structure.getStructureId());
      }
    }
  }

  private static <T> T getService(Class<T> serviceClass) {
    var bundleContext = FrameworkUtil.getBundle(StructureInitializerTest.class).getBundleContext();
    return bundleContext.getService(bundleContext.getServiceReference(serviceClass));
  }
}
