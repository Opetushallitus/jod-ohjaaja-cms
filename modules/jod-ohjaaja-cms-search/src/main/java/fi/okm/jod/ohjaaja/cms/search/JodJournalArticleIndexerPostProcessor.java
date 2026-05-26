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

/**
 * Indexer post processor for JournalArticle document enhancement.
 *
 * <p>This processor enriches search index documents for Journal Articles by extracting and indexing
 * text content from attached files (documents) within form fields. It handles:
 *
 * <p>- Dynamic Data Mapping (DDM) form field processing for multi-locale articles - Document
 * Library (DL) file extraction and text indexing - Text extraction from various document formats
 * (PDF, Word, etc.) - Caching of extracted text to improve performance - Filtering of files based
 * on extension blacklists - Nested form field value traversal for complex document structures
 *
 * <p>The processor enables full-text search across attached files when users search for Journal
 * Article content, significantly improving search result relevance.
 *
 * @see IndexerPostProcessor
 */
@Component(
    immediate = true,
    property = {
      "indexer.class.name=com.liferay.journal.model.JournalArticle",
    },
    service = IndexerPostProcessor.class)
public class JodJournalArticleIndexerPostProcessor implements IndexerPostProcessor {

  private static final Log log = LogFactoryUtil.getLog(JodJournalArticleIndexerPostProcessor.class);

  /** Cache size constant indicating no size limit on cached extracted text. */
  private static final int CACHE_ANY_SIZE = -1;

  @Reference private DDMStructureLocalService ddmStructureLocalService;
  @Reference private JSONFactory jsonFactory;
  @Reference private DLFileEntryLocalService dlFileEntryLocalService;
  @Reference private InputStreamSanitizer inputStreamSanitizer;
  @Reference private PrefsProps prefsProps;
  @Reference private TextExtractor textExtractor;
  @Reference private Localization localization;
  @Reference private DLStore dlStore;

  /**
   * Post-processes indexed documents to enhance indexing with file content.
   *
   * <p>Extracts text from Document Library files attached to Journal Article form fields and
   * appends the extracted content to the searchable field value. This enables full-text search
   * across document attachments.
   *
   * <p>Processing includes: - Retrieval of DDM structure and form values from the article -
   * Iteration through all available locales - File content extraction with text formatting -
   * Appending extracted text to document content fields
   *
   * @param document the search document being indexed
   * @param object the source object (expected to be JournalArticle)
   */
  @Override
  public void postProcessDocument(Document document, Object object) {

    if (object instanceof JournalArticle journalArticle) {
      // Retrieve the Dynamic Data Mapping structure for form field definitions
      var ddmStructure =
          ddmStructureLocalService.fetchStructure(journalArticle.getDDMStructureId());
      var ddmFormValues = journalArticle.getDDMFormValues();
      if (ddmFormValues != null) {
        // Process each available locale to handle multi-language content
        ddmFormValues
            .getAvailableLocales()
            .forEach(
                locale -> {
                  // Get the localized content field from the search document
                  var field =
                      document.getField(
                          localization.getLocalizedName(
                              Field.CONTENT, LocaleUtil.toLanguageId(locale)));

                  if (field == null || field.getValue() == null) {
                    return;
                  }

                  // Append extracted file contents to the existing content for full-text search
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

  /**
   * Extracts text content from all Document Library files in form field values.
   *
   * <p>Recursively processes form field values to handle nested fields and extract text from
   * document library file attachments.
   *
   * @param ddmFormFieldValues the list of form field values to process
   * @param ddmStructure the DDM structure containing field definitions
   * @param defaultLocale the default locale for field data retrieval
   * @param locale the current locale being processed
   * @return concatenated extracted text from all document library files
   */
  private String getDLFileContents(
      List<DDMFormFieldValue> ddmFormFieldValues,
      DDMStructure ddmStructure,
      Locale defaultLocale,
      Locale locale) {

    return ddmFormFieldValues.stream()
        .flatMap(
            ddmFormFieldValue -> {
              // Process nested form field values (for repeatable/complex fields)
              var nestedFieldValuesContents =
                  ddmFormFieldValue.getNestedDDMFormFieldValues().stream()
                      .flatMap(
                          nestedDDMFormFieldValue ->
                              getDLFileContent(
                                  nestedDDMFormFieldValue, ddmStructure, defaultLocale, locale));
              // Process the main form field value
              var dlFileContent =
                  getDLFileContent(ddmFormFieldValue, ddmStructure, defaultLocale, locale);
              // Combine nested and main field contents
              return Stream.concat(nestedFieldValuesContents, dlFileContent);
            })
        .collect(Collectors.joining());
  }

  /**
   * Extracts text content from a single Document Library file field if applicable.
   *
   * <p>Validates that: - The field is a document library type field - The field indexing type is
   * enabled (not "none") - The file is not in the extension blacklist
   *
   * <p>Text is cached for performance, and the cache is invalidated if the cached content exceeds
   * the configured file indexing size limit.
   *
   * @param ddmFormFieldValue the form field value containing potential file reference
   * @param ddmStructure the DDM structure containing field configuration
   * @param defaultLocale the default locale for field value retrieval
   * @param locale the current locale being processed
   * @return stream containing extracted text (empty if not applicable or extraction fails)
   */
  private Stream<String> getDLFileContent(
      DDMFormFieldValue ddmFormFieldValue,
      DDMStructure ddmStructure,
      Locale defaultLocale,
      Locale locale) {

    var ddmFormField = ddmFormFieldValue.getDDMFormField();

    // Retrieve the index type configuration for this field
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

    // Exit early if field is not indexable or not a document library field
    if (Validator.isNull(indexType)
        || "none".equals(indexType)
        || value == null
        || !DDMFormFieldTypeConstants.DOCUMENT_LIBRARY.equals(ddmFormField.getType())) {
      return Stream.empty();
    }

    // Determine the effective locale for field value retrieval
    var ddmFormFieldLocale = ddmFormField.isLocalizable() ? locale : LocaleUtil.ROOT;
    // Deserialize the field value to get the actual data
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
      // Parse the JSON object containing file entry reference
      var jsonObject = jsonFactory.createJSONObject(String.valueOf(serializable));
      if (jsonObject == null || !jsonObject.has("fileEntryId")) {
        return Stream.empty();
      }

      // Retrieve the file entry and extract its text content
      var fileEntryId = jsonObject.getString("fileEntryId");
      var fileEntry = dlFileEntryLocalService.getDLFileEntry(Long.parseLong(fileEntryId));
      var text = extractText(fileEntry);

      if (text != null) {
        // Return text with leading space for proper word boundary separation
        return Stream.of(StringPool.SPACE.concat(text));
      }
    } catch (Exception exception) {
      if (log.isDebugEnabled()) {
        log.debug(exception);
      }
    }
    return Stream.empty();
  }

  /**
   * Generates the cache index label for a document library file entry.
   *
   * <p>The label is constructed from the file version's store filename with an ".index" suffix to
   * distinguish cached extracted text from original files.
   *
   * @param dlFileEntry the document library file entry
   * @return the cache index label for this file entry
   * @throws PortalException if file version cannot be retrieved
   */
  private String getIndexVersionLabel(DLFileEntry dlFileEntry) throws PortalException {
    var dlFileVersion = dlFileEntry.getFileVersion();
    return dlFileVersion.getStoreFileName() + ".index";
  }

  /**
   * Extracts text content from a document library file entry.
   *
   * <p>Implements a caching strategy: - Checks for existing cached extracted text - Returns cached
   * content if within size limits - Invalidates cache if content exceeds indexing size limit -
   * Extracts and caches new text if not cached or cache is stale
   *
   * <p>Text extraction respects the DL_FILE_INDEXING_MAX_SIZE configuration.
   *
   * @param dlFileEntry the document library file entry to process
   * @return extracted text content, or null if extraction fails or file is not indexable
   * @throws PortalException if file information cannot be retrieved
   * @throws IOException if stream operations fail
   */
  private String extractText(DLFileEntry dlFileEntry) throws PortalException, IOException {

    // Get the maximum file size allowed for indexing
    var dlFileIndexingMaxSize =
        GetterUtil.getInteger(PropsUtil.get(PropsKeys.DL_FILE_INDEXING_MAX_SIZE));

    var indexVersionLabel = getIndexVersionLabel(dlFileEntry);

    // Check if cached extracted text exists
    if (dlStore.hasFile(
        dlFileEntry.getCompanyId(),
        dlFileEntry.getDataRepositoryId(),
        dlFileEntry.getName(),
        indexVersionLabel)) {

      // Retrieve cached content
      var cachedContent =
          StreamUtil.toString(
              dlStore.getFileAsStream(
                  dlFileEntry.getCompanyId(),
                  dlFileEntry.getDataRepositoryId(),
                  dlFileEntry.getName(),
                  indexVersionLabel));

      // Return cached content if within size limits (or no limit set)
      if (cachedContent.length() <= dlFileIndexingMaxSize
          || dlFileIndexingMaxSize == CACHE_ANY_SIZE) {
        return cachedContent;
      }

      // Invalidate cache if content exceeds size limit
      dlStore.deleteFile(
          dlFileEntry.getCompanyId(),
          dlFileEntry.getDataRepositoryId(),
          dlFileEntry.getName(),
          indexVersionLabel);
    }

    // Extract text from the original file
    var inputStream = getInputStream(dlFileEntry);

    if (inputStream == null) {
      return null;
    }

    var text = textExtractor.extractText(inputStream, dlFileIndexingMaxSize);

    // Cache the extracted text for future use
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

  /**
   * Retrieves a sanitized input stream for a document library file entry.
   *
   * <p>Validates that the file should be indexed based on its extension against the
   * DL_FILE_INDEXING_IGNORE_EXTENSIONS blacklist, then retrieves and sanitizes the file content
   * stream.
   *
   * @param dlFileEntry the document library file entry
   * @return sanitized input stream for the file, or null if file should not be indexed or retrieval
   *     fails
   */
  private InputStream getInputStream(DLFileEntry dlFileEntry) {
    try {
      // Check if file extension is in the ignore list
      if (!isIndexContent(dlFileEntry)) {
        return null;
      }

      // Get the current file version and its sanitized content stream
      var dlFileVersion = dlFileEntry.getFileVersion();

      return inputStreamSanitizer.sanitize(dlFileVersion.getContentStream(false));

    } catch (PortalException portalException) {
      if (log.isDebugEnabled()) {
        log.debug("Unable to get input stream", portalException);
      }
      return null;
    }
  }

  /**
   * Checks whether a document library file should be indexed based on its extension.
   *
   * <p>Returns false if the file extension is in the DL_FILE_INDEXING_IGNORE_EXTENSIONS blacklist,
   * preventing indexing of unsupported or sensitive file types.
   *
   * @param dlFileEntry the document library file entry to check
   * @return true if the file should be indexed, false if its extension is blacklisted
   */
  private boolean isIndexContent(DLFileEntry dlFileEntry) {
    var ignoreExtensions =
        prefsProps.getStringArray(PropsKeys.DL_FILE_INDEXING_IGNORE_EXTENSIONS, StringPool.COMMA);

    return !ArrayUtil.contains(ignoreExtensions, StringPool.PERIOD + dlFileEntry.getExtension());
  }

  /**
   * Post-processes full search queries (no-op for this processor).
   *
   * <p>This processor only customizes document indexing and does not need to modify the full search
   * query structure or constraints.
   *
   * @param booleanQuery the full search query
   * @param searchContext the search context
   */
  @Override
  public void postProcessFullQuery(BooleanQuery booleanQuery, SearchContext searchContext) {
    // No-op: this post processor only customizes document indexing via postProcessDocument and
    // does not need to modify the full search query.
  }

  /**
   * Post-processes search queries and filters (no-op for this processor).
   *
   * <p>This processor does not need to adjust dynamic search queries or their runtime filter
   * constraints.
   *
   * @param booleanQuery the search query
   * @param booleanFilter the search filter
   * @param searchContext the search context
   */
  @Override
  public void postProcessSearchQuery(
      BooleanQuery booleanQuery, BooleanFilter booleanFilter, SearchContext searchContext) {
    // No-op: this post processor does not need to adjust the search query or its filters.
  }

  /**
   * Post-processes search result summaries (no-op for this processor).
   *
   * <p>This processor does not customize how search results are displayed in search summaries or
   * result previews.
   *
   * @param summary the search result summary
   * @param document the search document
   * @param locale the locale for the result
   * @param s additional summary parameter
   */
  @Override
  public void postProcessSummary(Summary summary, Document document, Locale locale, String s) {
    // No-op: this post processor does not customize the search result summary.
  }

  /**
   * Post-processes context-level boolean filters (no-op for this processor).
   *
   * <p>This processor does not add or modify context-level filters that apply globally to all
   * search queries.
   *
   * @param booleanFilter the context-level boolean filter
   * @param searchContext the search context
   */
  @Override
  public void postProcessContextBooleanFilter(
      BooleanFilter booleanFilter, SearchContext searchContext) {
    // No-op: this post processor does not add context-level boolean filters.
  }
}
