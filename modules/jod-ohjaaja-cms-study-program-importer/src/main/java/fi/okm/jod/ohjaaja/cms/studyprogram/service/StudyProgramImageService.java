/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.studyprogram.service;

import static fi.okm.jod.ohjaaja.cms.studyprogram.util.StudyProgramArticleFields.IMAGE_FIELD_NAME;

import com.liferay.journal.model.JournalArticle;
import com.liferay.portal.kernel.json.JSONException;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.util.Validator;
import fi.okm.jod.ohjaaja.cms.studyprogram.util.StudyProgramArticleValues;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Chooses the image that a study program article is built with, and tells which image an article
 * shows.
 */
@Component(service = StudyProgramImageService.class)
public class StudyProgramImageService {

  private static final Log log = LogFactoryUtil.getLog(StudyProgramImageService.class);

  @Reference private StudyProgramFileService studyProgramFileService;

  /**
   * Imports the image of the study program, or uses the placeholder if it has none. If the image
   * cannot be imported, the article keeps the image it shows until the image can be imported, and a
   * new article shows the placeholder meanwhile.
   *
   * @param journalArticle the latest version of the article, or null if there is none yet
   * @param placeholder the image of the articles of study programs without one, or null if it is
   *     missing
   */
  public ArticleImage getImage(
      StudyProgramArticleValues values,
      JournalArticle journalArticle,
      FileEntry placeholder,
      long userId)
      throws InterruptedException {
    if (values.imageUrl() == null) {
      if (placeholder == null) {
        log.warn("Placeholder image missing");
      }
      return new ArticleImage(placeholder, placeholder != null);
    }

    var image =
        studyProgramFileService.getStudyProgramImage(values.imageUrl(), values.oid(), userId);
    if (image != null) {
      return new ArticleImage(image, true);
    }
    log.warn("Image import failed " + values.imageUrl());
    // Not the file that an earlier import stored for the study program, which the article may no
    // longer show, such as after the study program has had no image for a while
    var shownImage = journalArticle == null ? null : getShownImage(journalArticle);
    return new ArticleImage(shownImage != null ? shownImage : placeholder, false);
  }

  /**
   * @return true if the article shows the given image with its current file name, or no image if it
   *     is null. Whether the image still exists does not matter.
   */
  public boolean showsImage(JournalArticle journalArticle, FileEntry image) {
    var articleImage = getImageFieldValue(journalArticle);
    if (articleImage == null || image == null) {
      return articleImage == null && image == null;
    }
    return image.getUuid().equals(articleImage.getString("uuid"))
        && image.getGroupId() == articleImage.getLong("groupId")
        && image.getFileName().equals(articleImage.getString("name"));
  }

  /**
   * @return the image that the article shows, or null if it has none, or its image has been deleted
   *     or moved to the Recycle Bin
   */
  public FileEntry getShownImage(JournalArticle journalArticle) {
    var image = getImageFieldValue(journalArticle);
    if (image == null) {
      return null;
    }
    var fileEntry =
        studyProgramFileService.findImage(image.getString("uuid"), image.getLong("groupId"));
    if (fileEntry == null) {
      log.info("Image of article " + journalArticle.getExternalReferenceCode() + " is missing");
    }
    return fileEntry;
  }

  /**
   * @return the value of the image field of the article, or null if it has no image
   */
  private static JSONObject getImageFieldValue(JournalArticle journalArticle) {
    var imageFieldValues =
        journalArticle.getDDMFormValues().getDDMFormFieldValuesMap(false).get(IMAGE_FIELD_NAME);
    if (imageFieldValues == null
        || imageFieldValues.isEmpty()
        || imageFieldValues.getFirst().getValue() == null) {
      return null;
    }
    var value = imageFieldValues.getFirst().getValue();
    var imageJson = value.getString(value.getDefaultLocale());
    if (Validator.isBlank(imageJson)) {
      return null;
    }
    try {
      var image = JSONFactoryUtil.createJSONObject(imageJson);
      return Validator.isBlank(image.getString("uuid")) ? null : image;
    } catch (JSONException e) {
      log.warn(
          "Image of article "
              + journalArticle.getExternalReferenceCode()
              + " is not valid: "
              + e.getMessage());
      return null;
    }
  }

  /**
   * @param fileEntry the image of the article, or null if it has none
   * @param imported false if the image or the placeholder could not be imported, in which case the
   *     article has the image it already showed, the placeholder or no image
   */
  public record ArticleImage(FileEntry fileEntry, boolean imported) {}
}
