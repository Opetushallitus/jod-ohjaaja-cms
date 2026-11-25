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
import static fi.okm.jod.ohjaaja.cms.util.StructureInitializer.NEWS_FOLDER_EXTERNAL_REFERENCE_CODE;
import static fi.okm.jod.ohjaaja.cms.util.StructureInitializer.NEWS_STRUCTURE_EXTERNAL_REFERENCE_CODE;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.dynamic.data.mapping.service.DDMStructureLocalService;
import com.liferay.headless.delivery.dto.v1_0.StructuredContent;
import com.liferay.headless.delivery.resource.v1_0.StructuredContentResource;
import com.liferay.journal.model.JournalArticle;
import com.liferay.journal.service.JournalFolderLocalService;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import com.liferay.portal.vulcan.extension.ExtensionProviderRegistry;
import com.liferay.portal.vulcan.extension.PropertyDefinition;
import fi.okm.jod.ohjaaja.cms.util.JodOhjaajaCmsUtil;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** The contentType field of the structured contents, as an anonymous user of the UI reads it. */
@RunWith(Arquillian.class)
public class ContentTypeExtensionProviderTest {

  @ClassRule @Rule
  public static final LiferayIntegrationTestRule liferayIntegrationTestRule =
      new LiferayIntegrationTestRule();

  @Inject private DDMStructureLocalService ddmStructureLocalService;
  @Inject private JodOhjaajaCmsUtil jodOhjaajaCmsUtil;
  @Inject private JournalFolderLocalService journalFolderLocalService;
  @Inject private StructuredContentResource.Factory structuredContentResourceFactory;
  @Inject private ExtensionProviderRegistry extensionProviderRegistry;

  private StructuredContentResource structuredContentResource;
  private long groupId;
  // Unique in the titles, to find the contents of this test in the search-backed list
  private String token;
  private final List<Long> structuredContentIds = new ArrayList<>();

  @Before
  public void setUp() throws Exception {
    groupId = jodOhjaajaCmsUtil.getJodOhjaajaCmsGroup().getGroupId();
    token = "contenttype" + UUID.randomUUID().toString().replace("-", "");
    structuredContentResource =
        structuredContentResourceFactory
            .create()
            .user(TestPropsValues.getUser())
            .preferredLocale(LocaleUtil.fromLanguageId("en_US"))
            .checkPermissions(false)
            .build();
  }

  @After
  public void tearDown() throws Exception {
    for (var structuredContentId : structuredContentIds) {
      structuredContentResource.deleteStructuredContent(structuredContentId);
    }
  }

  @Test
  public void shouldReturnContentTypeByExternalReferenceCode() throws Exception {
    var article = addArticle();
    var news = addNews();

    Assert.assertEquals("ARTICLE", getByExternalReferenceCode(article).getString("contentType"));
    Assert.assertEquals("NEWS", getByExternalReferenceCode(news).getString("contentType"));
  }

  @Test
  public void shouldReturnContentTypeInList() throws Exception {
    var article = addArticle();
    var news = addNews();

    var contentTypes =
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofMillis(500))
            .until(this::getContentTypesOfList, found -> found.size() == 2);

    Assert.assertEquals(Map.of(article.getId(), "ARTICLE", news.getId(), "NEWS"), contentTypes);
  }

  @Test
  public void shouldDefineContentType() throws Exception {
    var definitions =
        extensionProviderRegistry
            .getExtensionProviders(
                TestPropsValues.getCompanyId(), StructuredContent.class.getName())
            .stream()
            .map(
                provider -> {
                  try {
                    return provider.getExtendedPropertyDefinitions(
                        TestPropsValues.getCompanyId(), StructuredContent.class.getName());
                  } catch (Exception e) {
                    throw new IllegalStateException(e);
                  }
                })
            .filter(Objects::nonNull)
            .filter(found -> found.containsKey("contentType"))
            .toList();

    Assert.assertEquals(1, definitions.size());
    var definition = definitions.get(0).get("contentType");
    Assert.assertEquals(PropertyDefinition.PropertyType.TEXT, definition.getPropertyType());
    Assert.assertFalse(definition.isRequired());
  }

  private JSONObject getByExternalReferenceCode(StructuredContent content) throws Exception {
    return JSONFactoryUtil.createJSONObject(
        guestGet(
            "/headless-delivery/v1.0/sites/%d/structured-contents/by-external-reference-code/%s"
                .formatted(groupId, encode(content.getExternalReferenceCode()))));
  }

  private Map<Long, String> getContentTypesOfList() throws Exception {
    var items =
        JSONFactoryUtil.createJSONObject(
                guestGet(
                    // flatten: the news are in a folder, not at the root of the site
                    "/headless-delivery/v1.0/sites/%d/structured-contents?flatten=true&search=%s"
                        .formatted(groupId, encode(token))))
            .getJSONArray("items");
    var contentTypes = new HashMap<Long, String>();
    for (var i = 0; i < items.length(); i++) {
      var item = items.getJSONObject(i);
      if (structuredContentIds.contains(item.getLong("id"))) {
        contentTypes.put(item.getLong("id"), item.getString("contentType"));
      }
    }
    return contentTypes;
  }

  private StructuredContent addArticle() throws Exception {
    var content =
        structuredContentResource.postSiteStructuredContent(
            groupId, toDTO(getStructureId(ARTICLE_STRUCTURE_EXTERNAL_REFERENCE_CODE)));
    structuredContentIds.add(content.getId());
    return content;
  }

  private StructuredContent addNews() throws Exception {
    var folder =
        journalFolderLocalService.fetchJournalFolderByExternalReferenceCode(
            NEWS_FOLDER_EXTERNAL_REFERENCE_CODE, groupId);
    Assert.assertNotNull("Folder " + NEWS_FOLDER_EXTERNAL_REFERENCE_CODE + " not found", folder);
    var content =
        structuredContentResource.postStructuredContentFolderStructuredContent(
            folder.getFolderId(), toDTO(getStructureId(NEWS_STRUCTURE_EXTERNAL_REFERENCE_CODE)));
    structuredContentIds.add(content.getId());
    return content;
  }

  private long getStructureId(String externalReferenceCode) {
    var structure =
        ddmStructureLocalService.fetchStructureByExternalReferenceCode(
            externalReferenceCode, groupId, PortalUtil.getClassNameId(JournalArticle.class));
    Assert.assertNotNull("Structure " + externalReferenceCode + " not found", structure);
    return structure.getStructureId();
  }

  // The article and news structures have the same fields
  private StructuredContent toDTO(long structureId) {
    return StructuredContent.toDTO(
        """
        {
          "contentStructureId": %d,
          "title": "Sisältötyyppi %s",
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
                token,
                contentField("ingress", "Tiivistelmä"),
                contentField("content", "<p>Sisältö</p>"),
                contentField("linktext", "Linkki"),
                contentField("linkurl", "https://example.org")));
  }

  private static String contentField(String name, String data) {
    var value = JSONFactoryUtil.createJSONObject().put("data", data);
    return JSONFactoryUtil.createJSONObject()
        .put("name", name)
        .put("fieldReference", name)
        .put("contentFieldValue", value)
        .put("contentFieldValue_i18n", JSONFactoryUtil.createJSONObject().put("en-US", value))
        .toString();
  }

  private static String guestGet(String path) throws Exception {
    var url = "http://localhost:8080" + PortalUtil.getPathContext() + "/o" + path;
    var request =
        HttpRequest.newBuilder(URI.create(url))
            .header("Accept", "application/json")
            // The contents are only in en_US; the search must not depend on the default locale
            .header("Accept-Language", "en-US")
            .GET()
            .build();
    var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    Assert.assertEquals("GET " + url + ": " + response.body(), 200, response.statusCode());
    return response.body();
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
