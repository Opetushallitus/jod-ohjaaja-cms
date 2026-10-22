/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.studyprogram.service;

import static fi.okm.jod.ohjaaja.cms.studyprogram.constants.StudyProgramImporterConstants.STUDY_PROGRAM_FINGERPRINT_FIELD_NAME;

import com.liferay.expando.kernel.model.ExpandoColumnConstants;
import com.liferay.expando.kernel.util.ExpandoBridgeFactoryUtil;
import com.liferay.journal.model.JournalArticle;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.UnicodeProperties;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import java.util.Map;
import org.osgi.service.component.annotations.Component;

/**
 * Stores the {@link fi.okm.jod.ohjaaja.cms.studyprogram.util.StudyProgramFingerprint} of the study
 * program data each article was last built from, so that the import updates an article only when
 * the study program has changed or the article has been edited after the import. Updating an
 * unchanged article would create a new version and change its modification date, which is shown in
 * the sitemap.
 *
 * <p>The fingerprint is stored together with the article version that the import created. Liferay
 * copies the stored value to every new version, so a version created by editing the article does
 * not match it.
 */
@Component(service = StudyProgramFingerprintService.class)
public class StudyProgramFingerprintService {

  private static final Log log = LogFactoryUtil.getLog(StudyProgramFingerprintService.class);
  private static final String IMAGE_MISSING_SUFFIX = "/image-missing";

  /** The properties of the custom field that hide it. */
  private static final Map<String, String> FINGERPRINT_FIELD_PROPERTIES =
      Map.of(
          ExpandoColumnConstants.PROPERTY_HIDDEN,
          "true",
          ExpandoColumnConstants.PROPERTY_VISIBLE_WITH_UPDATE_PERMISSION,
          "false",
          ExpandoColumnConstants.INDEX_TYPE,
          String.valueOf(ExpandoColumnConstants.INDEX_TYPE_NONE));

  /**
   * Creates the custom field that holds the fingerprint, if it does not exist yet, and hides it.
   */
  public void initFingerprintField() throws PortalException {
    var expandoBridge =
        ExpandoBridgeFactoryUtil.getExpandoBridge(
            PortalUtil.getDefaultCompanyId(), JournalArticle.class.getName());
    if (!expandoBridge.hasAttribute(STUDY_PROGRAM_FINGERPRINT_FIELD_NAME)) {
      expandoBridge.addAttribute(
          STUDY_PROGRAM_FINGERPRINT_FIELD_NAME, ExpandoColumnConstants.STRING, false);
      log.info("Added custom field " + STUDY_PROGRAM_FINGERPRINT_FIELD_NAME);
    }

    // Only these properties are compared, since the field can also have others, such as defaults
    // that Liferay adds. Those are kept.
    var properties = new UnicodeProperties(true);
    properties.putAll(expandoBridge.getAttributeProperties(STUDY_PROGRAM_FINGERPRINT_FIELD_NAME));
    var changed = false;
    for (var property : FINGERPRINT_FIELD_PROPERTIES.entrySet()) {
      if (!property.getValue().equals(properties.getProperty(property.getKey()))) {
        properties.setProperty(property.getKey(), property.getValue());
        changed = true;
      }
    }
    if (changed) {
      expandoBridge.setAttributeProperties(STUDY_PROGRAM_FINGERPRINT_FIELD_NAME, properties, false);
    }
  }

  /**
   * @return how the given latest version of an article relates to the study program data with the
   *     given fingerprint
   */
  public ArticleState getArticleState(JournalArticle journalArticle, String fingerprint) {
    if (journalArticle.getStatus() != WorkflowConstants.STATUS_APPROVED) {
      return ArticleState.OUTDATED;
    }
    var storedValue =
        journalArticle.getExpandoBridge().getAttribute(STUDY_PROGRAM_FINGERPRINT_FIELD_NAME, false);
    var value = getStoredValue(journalArticle, fingerprint);
    if (value.equals(storedValue)) {
      return ArticleState.UP_TO_DATE;
    }
    if ((value + IMAGE_MISSING_SUFFIX).equals(storedValue)) {
      return ArticleState.IMAGE_MISSING;
    }
    return ArticleState.OUTDATED;
  }

  public void setFingerprint(
      JournalArticle journalArticle, String fingerprint, boolean imageImported) {
    var value =
        getStoredValue(journalArticle, fingerprint) + (imageImported ? "" : IMAGE_MISSING_SUFFIX);
    var expandoBridge = journalArticle.getExpandoBridge();
    // Not stored again when unchanged, such as when the image is still missing
    if (!value.equals(expandoBridge.getAttribute(STUDY_PROGRAM_FINGERPRINT_FIELD_NAME, false))) {
      expandoBridge.setAttribute(STUDY_PROGRAM_FINGERPRINT_FIELD_NAME, value, false);
    }
  }

  private static String getStoredValue(JournalArticle journalArticle, String fingerprint) {
    return fingerprint + "@" + journalArticle.getVersion();
  }

  public enum ArticleState {
    /** The article was built from the study program data and has not been changed since. */
    UP_TO_DATE,
    /** Like {@link #UP_TO_DATE}, but the image could not be imported. */
    IMAGE_MISSING,
    /**
     * The study program data has changed, or the article has been edited, unpublished or never
     * fingerprinted.
     */
    OUTDATED
  }
}
