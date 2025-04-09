/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.search;

import com.liferay.document.library.kernel.model.DLFileEntry;
import com.liferay.document.library.kernel.service.DLFileEntryLocalService;
import com.liferay.document.library.kernel.store.DLStore;
import com.liferay.document.library.kernel.store.DLStoreRequest;
import com.liferay.document.library.security.io.InputStreamSanitizer;
import com.liferay.dynamic.data.mapping.form.field.type.constants.DDMFormFieldTypeConstants;
import com.liferay.dynamic.data.mapping.model.*;
import com.liferay.dynamic.data.mapping.service.DDMStructureLocalService;
import com.liferay.dynamic.data.mapping.storage.DDMFormFieldValue;
import com.liferay.dynamic.data.mapping.storage.constants.FieldConstants;
import com.liferay.journal.model.JournalArticle;
import com.liferay.petra.io.StreamUtil;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.search.*;
import com.liferay.portal.kernel.search.filter.BooleanFilter;
import com.liferay.portal.kernel.util.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(
    immediate = true,
    property = {
      "indexer.class.name=com.liferay.journal.model.JournalArticle",
    },
    service = IndexerPostProcessor.class)
public class JodJournalArticleIndexerPostProcessor implements IndexerPostProcessor {

  private static final Log log = LogFactoryUtil.getLog(JodJournalArticleIndexerPostProcessor.class);
  private static final int CACHE_ANY_SIZE = -1;

  @Reference private DDMStructureLocalService ddmStructureLocalService;
  @Reference private JSONFactory jsonFactory;
  @Reference private DLFileEntryLocalService dlFileEntryLocalService;
  @Reference private InputStreamSanitizer inputStreamSanitizer;
  @Reference private PrefsProps prefsProps;
  @Reference private TextExtractor textExtractor;
  @Reference private Localization localization;
  @Reference private DLStore dlStore;

  @Override
  public void postProcessDocument(Document document, Object object) {

    if (object instanceof JournalArticle journalArticle) {
      var ddmStructure =
          ddmStructureLocalService.fetchStructure(journalArticle.getDDMStructureId());
      var ddmFormValues = journalArticle.getDDMFormValues();
      if (ddmFormValues != null) {
        ddmFormValues
            .getAvailableLocales()
            .forEach(
                locale -> {
                  var field =
                      document.getField(
                          localization.getLocalizedName(
                              Field.CONTENT, LocaleUtil.toLanguageId(locale)));

                  if (field == null || field.getValue() == null) {
                    return;
                  }

                  var content =
                      field.getValue()
                          + getDLFileContents(
                              ddmFormValues.getDDMFormFieldValues(),
                              ddmStructure,
                              ddmFormValues.getDefaultLocale(),
                              locale);

                  field.setValue(content);
                });
      }
    }
  }

  private String getDLFileContents(
      List<DDMFormFieldValue> ddmFormFieldValues,
      DDMStructure ddmStructure,
      Locale defaultLocale,
      Locale locale) {

    return ddmFormFieldValues.stream()
        .flatMap(
            ddmFormFieldValue -> {
              var nestedFieldValuesContents =
                  ddmFormFieldValue.getNestedDDMFormFieldValues().stream()
                      .flatMap(
                          nestedDDMFormFieldValue ->
                              getDLFileContent(
                                  nestedDDMFormFieldValue, ddmStructure, defaultLocale, locale));
              var dlFileContent =
                  getDLFileContent(ddmFormFieldValue, ddmStructure, defaultLocale, locale);
              return Stream.concat(nestedFieldValuesContents, dlFileContent);
            })
        .collect(Collectors.joining());
  }

  private Stream<String> getDLFileContent(
      DDMFormFieldValue ddmFormFieldValue,
      DDMStructure ddmStructure,
      Locale defaultLocale,
      Locale locale) {

    var ddmFormField = ddmFormFieldValue.getDDMFormField();

    String indexType;
    try {
      indexType =
          ddmFormField != null
              ? ddmStructure.getFieldProperty(ddmFormField.getName(), "indexType")
              : null;
    } catch (PortalException e) {
      indexType = null;
    }

    var value = ddmFormFieldValue.getValue();

    if (Validator.isNull(indexType)
        || "none".equals(indexType)
        || value == null
        || !DDMFormFieldTypeConstants.DOCUMENT_LIBRARY.equals(ddmFormField.getType())) {
      return Stream.empty();
    }

    var ddmFormFieldLocale = ddmFormField.isLocalizable() ? locale : LocaleUtil.ROOT;
    var serializable =
        FieldConstants.getSerializable(
            defaultLocale,
            ddmFormFieldLocale,
            ddmFormField.getDataType(),
            value.getString(ddmFormFieldLocale));

    if ((serializable == null) || Validator.isBlank(String.valueOf(serializable))) {
      return Stream.empty();
    }

    try {

      var jsonObject = jsonFactory.createJSONObject(String.valueOf(serializable));
      if (jsonObject == null || !jsonObject.has("fileEntryId")) {
        return Stream.empty();
      }
      var fileEntryId = jsonObject.getString("fileEntryId");
      var fileEntry = dlFileEntryLocalService.getDLFileEntry(Long.parseLong(fileEntryId));
      var text = _extractText(fileEntry);

      if (text != null) {
        return Stream.of(StringPool.SPACE.concat(text));
      }
    } catch (Exception exception) {
      if (log.isDebugEnabled()) {
        log.debug(exception);
      }
    }
    return Stream.empty();
  }

  private String _getIndexVersionLabel(DLFileEntry dlFileEntry) throws PortalException {
    var dlFileVersion = dlFileEntry.getFileVersion();
    return dlFileVersion.getStoreFileName() + ".index";
  }

  private String _extractText(DLFileEntry dlFileEntry) throws PortalException, IOException {

    var dlFileIndexingMaxSize =
        GetterUtil.getInteger(PropsUtil.get(PropsKeys.DL_FILE_INDEXING_MAX_SIZE));

    var indexVersionLabel = _getIndexVersionLabel(dlFileEntry);

    if (dlStore.hasFile(
        dlFileEntry.getCompanyId(),
        dlFileEntry.getDataRepositoryId(),
        dlFileEntry.getName(),
        indexVersionLabel)) {

      var cachedContent =
          StreamUtil.toString(
              dlStore.getFileAsStream(
                  dlFileEntry.getCompanyId(),
                  dlFileEntry.getDataRepositoryId(),
                  dlFileEntry.getName(),
                  indexVersionLabel));

      if (cachedContent.length() <= dlFileIndexingMaxSize
          || dlFileIndexingMaxSize == CACHE_ANY_SIZE) {
        return cachedContent;
      }

      dlStore.deleteFile(
          dlFileEntry.getCompanyId(),
          dlFileEntry.getDataRepositoryId(),
          dlFileEntry.getName(),
          indexVersionLabel);
    }

    var inputStream = _getInputStream(dlFileEntry);

    if (inputStream == null) {
      return null;
    }

    var text = textExtractor.extractText(inputStream, dlFileIndexingMaxSize);

    if (Validator.isNotNull(text)) {

      dlStore.addFile(
          DLStoreRequest.builder(
                  dlFileEntry.getCompanyId(),
                  dlFileEntry.getDataRepositoryId(),
                  dlFileEntry.getName())
              .versionLabel(indexVersionLabel)
              .build(),
          text.getBytes(StandardCharsets.UTF_8));
    }
    return text;
  }

  private InputStream _getInputStream(DLFileEntry dlFileEntry) {
    try {
      if (!_isIndexContent(dlFileEntry)) {
        return null;
      }

      var dlFileVersion = dlFileEntry.getFileVersion();

      return inputStreamSanitizer.sanitize(dlFileVersion.getContentStream(false));

    } catch (PortalException portalException) {
      if (log.isDebugEnabled()) {
        log.debug("Unable to get input stream", portalException);
      }
      return null;
    }
  }

  private boolean _isIndexContent(DLFileEntry dlFileEntry) {
    var ignoreExtensions =
        prefsProps.getStringArray(PropsKeys.DL_FILE_INDEXING_IGNORE_EXTENSIONS, StringPool.COMMA);

    return !ArrayUtil.contains(ignoreExtensions, StringPool.PERIOD + dlFileEntry.getExtension());
  }

  @Override
  public void postProcessFullQuery(BooleanQuery booleanQuery, SearchContext searchContext) {}

  @Override
  public void postProcessSearchQuery(
      BooleanQuery booleanQuery, BooleanFilter booleanFilter, SearchContext searchContext) {}

  @Override
  public void postProcessSummary(Summary summary, Document document, Locale locale, String s) {}

  @Override
  public void postProcessContextBooleanFilter(
      BooleanFilter booleanFilter, SearchContext searchContext) {}
}
