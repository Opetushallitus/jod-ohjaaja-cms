/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.sitemap.service;

import com.liferay.asset.kernel.service.AssetCategoryLocalService;
import com.liferay.asset.kernel.service.AssetVocabularyLocalService;
import com.liferay.document.library.kernel.service.DLAppLocalService;
import com.liferay.dynamic.data.mapping.storage.DDMFormFieldValue;
import com.liferay.journal.model.JournalArticle;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.json.JSONException;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.security.permission.resource.ModelResourcePermission;
import com.liferay.portal.kernel.util.HtmlUtil;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.URLCodec;
import com.liferay.portal.kernel.util.Validator;
import fi.okm.jod.ohjaaja.cms.sitemap.model.Language;
import fi.okm.jod.ohjaaja.cms.util.PermissionUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import org.jsoup.Jsoup;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Reads an article's content the way jod-ohjaaja-ui reads it from headless-delivery
 * (src/utils/cms.ts): structure fields by their field reference, and tags from the tag vocabulary.
 * Must be called with the guest's permission checker.
 */
@Component(service = ArticleContentReader.class)
public class ArticleContentReader {

  private static final String FIELD_INGRESS = "ingress";
  private static final String FIELD_IMAGE = "image";
  private static final String FIELD_CONTENT = "content";
  private static final String FIELD_DOCUMENT = "document";
  private static final String FIELD_LINK = "link";
  private static final String FIELD_LINK_TEXT = "linktext";
  private static final String FIELD_LINK_URL = "linkurl";

  private static final String TAG_VOCABULARY_EXTERNAL_REFERENCE_CODE = "jod-ohjaaja-cms-tags";

  private static final Log log = LogFactoryUtil.getLog(ArticleContentReader.class);

  @Reference private AssetCategoryLocalService assetCategoryLocalService;
  @Reference private AssetVocabularyLocalService assetVocabularyLocalService;
  @Reference private DLAppLocalService dlAppLocalService;

  @Reference(target = "(model.class.name=com.liferay.portal.kernel.repository.model.FileEntry)")
  private ModelResourcePermission<FileEntry> fileEntryModelResourcePermission;

  public ArticleContent read(JournalArticle article, Language language) {
    var locale = language.locale();
    // Like the UI, which finds the first field with the reference, a repeated field shows only once
    var singleValues = new HashMap<String, String>();
    var documents = new ArrayList<ArticleContent.Link>();
    var links = new ArrayList<ArticleContent.Link>();

    var ddmFormValues = article.getDDMFormValues();
    if (ddmFormValues != null) {
      for (var fieldValue : ddmFormValues.getDDMFormFieldValues()) {
        switch (getFieldReference(fieldValue)) {
          case FIELD_INGRESS, FIELD_IMAGE, FIELD_CONTENT ->
              singleValues.putIfAbsent(
                  getFieldReference(fieldValue), getString(fieldValue, locale));
          case FIELD_DOCUMENT -> {
            var document = getDocument(getString(fieldValue, locale));
            if (document != null) {
              documents.add(document);
            }
          }
          case FIELD_LINK -> {
            var link = getLink(fieldValue, locale);
            if (link != null) {
              links.add(link);
            }
          }
          default -> {
            // Not shown on the article page
          }
        }
      }
    }

    return new ArticleContent(
        article.getTitle(language.languageId()),
        toPlainText(article.getDescription(language.languageId())),
        toPlainText(singleValues.get(FIELD_INGRESS)),
        SitemapDates.toLocalDate(article.getCreateDate()),
        SitemapDates.toLocalDate(article.getModifiedDate()),
        getImageDescription(singleValues.get(FIELD_IMAGE)),
        singleValues.getOrDefault(FIELD_CONTENT, ""),
        documents,
        links,
        getKeywords(article, language));
  }

  private static String getFieldReference(DDMFormFieldValue fieldValue) {
    var ddmFormField = fieldValue.getDDMFormField();
    if (ddmFormField != null && Validator.isNotNull(ddmFormField.getFieldReference())) {
      return ddmFormField.getFieldReference();
    }
    return fieldValue.getName() != null ? fieldValue.getName() : "";
  }

  private static String getString(DDMFormFieldValue fieldValue, Locale locale) {
    var value = fieldValue.getValue();
    if (value == null) {
      return "";
    }
    var string = value.getString(locale);
    return string != null ? string : "";
  }

  private static String getImageDescription(String json) {
    var jsonObject = toJSONObject(json);
    if (jsonObject == null) {
      return "";
    }
    // headless-delivery returns the alt text as the image's description
    return jsonObject.getString("alt", jsonObject.getString("description", ""));
  }

  private ArticleContent.Link getDocument(String json) {
    var jsonObject = toJSONObject(json);
    if (jsonObject == null) {
      return null;
    }
    try {
      FileEntry fileEntry;
      var fileEntryId = jsonObject.getLong("fileEntryId");
      if (fileEntryId > 0) {
        fileEntry = dlAppLocalService.getFileEntry(fileEntryId);
      } else if (jsonObject.has("uuid") && jsonObject.has("groupId")) {
        fileEntry =
            dlAppLocalService.getFileEntryByUuidAndGroupId(
                jsonObject.getString("uuid"), jsonObject.getLong("groupId"));
      } else {
        return null;
      }
      if (!PermissionUtil.canView(fileEntryModelResourcePermission, fileEntry)) {
        return null;
      }
      // The same URL as the contentUrl of headless-delivery
      var url =
          PortalUtil.getPathContext()
              + "/documents/"
              + fileEntry.getRepositoryId()
              + "/"
              + fileEntry.getFolderId()
              + "/"
              + URLCodec.encodeURL(HtmlUtil.unescape(fileEntry.getTitle()), true)
              + "/"
              + fileEntry.getUuid();
      return new ArticleContent.Link(fileEntry.getTitle(), url);
    } catch (PortalException e) {
      if (log.isDebugEnabled()) {
        log.debug("Document not found: " + json, e);
      }
      return null;
    }
  }

  private static ArticleContent.Link getLink(DDMFormFieldValue fieldValue, Locale locale) {
    String text = null;
    String url = null;
    for (var nestedFieldValue : fieldValue.getNestedDDMFormFieldValues()) {
      switch (getFieldReference(nestedFieldValue)) {
        case FIELD_LINK_TEXT -> text = getString(nestedFieldValue, locale);
        case FIELD_LINK_URL -> url = getString(nestedFieldValue, locale);
        default -> {
          // Not part of a link
        }
      }
    }
    // Links without a text or a URL are not shown, as in the UI
    if (Validator.isBlank(text) || Validator.isBlank(url)) {
      return null;
    }
    return new ArticleContent.Link(text, url);
  }

  /**
   * The tags are not permission checked, like the taxonomyCategoryBriefs the UI shows them from.
   */
  private List<String> getKeywords(JournalArticle article, Language language) {
    var tagVocabulary =
        assetVocabularyLocalService.fetchAssetVocabularyByExternalReferenceCode(
            TAG_VOCABULARY_EXTERNAL_REFERENCE_CODE, article.getGroupId());
    if (tagVocabulary == null) {
      return List.of();
    }
    return assetCategoryLocalService
        .getCategories(JournalArticle.class.getName(), article.getResourcePrimKey())
        .stream()
        .filter(category -> category.getVocabularyId() == tagVocabulary.getVocabularyId())
        .map(category -> category.getTitle(language.languageId()))
        .filter(Validator::isNotNull)
        .toList();
  }

  private static JSONObject toJSONObject(String json) {
    if (Validator.isBlank(json)) {
      return null;
    }
    try {
      return JSONFactoryUtil.createJSONObject(json);
    } catch (JSONException e) {
      return null;
    }
  }

  private static String toPlainText(String html) {
    return Validator.isBlank(html) ? "" : Jsoup.parse(html).text();
  }
}
