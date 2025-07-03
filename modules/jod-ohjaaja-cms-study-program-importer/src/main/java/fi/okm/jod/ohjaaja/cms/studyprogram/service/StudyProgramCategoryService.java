/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.studyprogram.service;

import com.liferay.asset.kernel.model.AssetCategory;
import com.liferay.asset.kernel.model.AssetCategoryConstants;
import com.liferay.asset.kernel.model.AssetVocabulary;
import com.liferay.asset.kernel.model.AssetVocabularyConstants;
import com.liferay.asset.kernel.service.AssetCategoryLocalService;
import com.liferay.asset.kernel.service.AssetVocabularyLocalService;
import com.liferay.journal.model.JournalArticle;
import com.liferay.journal.service.JournalArticleLocalService;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.security.auth.GuestOrUserUtil;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.vulcan.util.LocalizedMapUtil;
import com.liferay.portlet.asset.util.AssetVocabularySettingsHelper;
import fi.okm.jod.ohjaaja.cms.studyprogram.constants.StudyProgramImporterConstants;
import fi.okm.jod.ohjaaja.cms.studyprogram.util.StudyProgramImporterUtil;
import fi.okm.jod.ohjaaja.cms.tags.dto.JodTaxonomyCategoryDto;
import fi.okm.jod.ohjaaja.cms.tags.service.TagsService;
import java.util.Map;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = StudyProgramCategoryService.class, immediate = true)
public class StudyProgramCategoryService {

  private static final Log log = LogFactoryUtil.getLog(StudyProgramCategoryService.class);

  @Reference private AssetVocabularyLocalService assetVocabularyLocalService;
  @Reference private AssetCategoryLocalService assetCategoryLocalService;
  @Reference private JournalArticleLocalService journalArticleLocalService;
  @Reference private Portal portal;
  @Reference private TagsService tagsService;

  @Activate
  protected void activate() {
    log.info("Study Program Importer Portlet activated");
    initializeStudyProgramTag();
    try {
      initializeStudyProgramCategory();
    } catch (PortalException e) {
      log.error("Failed to initialize study program category", e);
    }
  }

  public void setJournalArticleCategories(JournalArticle article) throws PortalException {
    var categoryIds =
        new long[] {
          getStudyProgramTagCategory().id(),
          getStudyProgramCategory().getCategoryId(),
          getStudyProgramParentCategory().getCategoryId()
        };

    var user = StudyProgramImporterUtil.getUser(PortalUtil.getDefaultCompanyId());
    journalArticleLocalService.updateAsset(user.getUserId(), article, categoryIds, null, null, 0.0);
  }

  private JodTaxonomyCategoryDto getStudyProgramTagCategory() {
    var tagCategories =
        tagsService.getJodTaxonomyCategories(StudyProgramImporterConstants.JOD_GROUP_ID);

    return tagCategories.stream()
        .filter(
            tagCategory ->
                StudyProgramImporterConstants.STUDY_PROGRAM_TAG_CATEGORY_EXTERNAL_REFERENCE_CODE
                    .equals(tagCategory.externalReferenceCode()))
        .findFirst()
        .orElse(null);
  }

  private AssetCategory getStudyProgramCategory() {
    return getStudyProgramCategoryVocabulary().getCategories().stream()
        .filter(
            assetCategory ->
                StudyProgramImporterConstants.STUDY_PROGRAM_CATEGORY_EXTERNAL_REFERENCE_CODE.equals(
                    assetCategory.getExternalReferenceCode()))
        .findFirst()
        .orElse(null);
  }

  private AssetCategory getStudyProgramParentCategory() {
    return getStudyProgramCategoryVocabulary().getCategories().stream()
        .filter(
            assetCategory ->
                StudyProgramImporterConstants.STUDY_PROGRAM_PARENT_CATEGORY_EXTERNAL_REFERENCE_CODE
                    .equals(assetCategory.getExternalReferenceCode()))
        .findFirst()
        .orElse(null);
  }

  private AssetVocabulary getStudyProgramCategoryVocabulary() {
    var vocabularies =
        assetVocabularyLocalService.getCompanyVocabularies(portal.getDefaultCompanyId());
    return vocabularies.stream()
        .filter(
            vocabulary ->
                StudyProgramImporterConstants
                    .STUDY_PROGRAM_CATEGORY_VOCABULARY_EXTERNAL_REFERENCE_CODE
                    .equals(vocabulary.getExternalReferenceCode()))
        .findFirst()
        .orElse(null);
  }

  private void initializeStudyProgramTag() {

    var tagCategory = getStudyProgramTagCategory();

    if (tagCategory != null) {
      tagsService.addOrUpdateJodTaxonomyCategory(
          tagCategory.id(),
          StudyProgramImporterConstants.STUDY_PROGRAM_TAG_CATEGORY_EXTERNAL_REFERENCE_CODE,
          StudyProgramImporterConstants.STUDY_PROGRAM_TAG_CATEGORY_FINNISH_TITLE,
          Map.of(
              "fi_FI",
              StudyProgramImporterConstants.STUDY_PROGRAM_TAG_CATEGORY_FINNISH_TITLE,
              "en_US",
              StudyProgramImporterConstants.STUDY_PROGRAM_TAG_CATEGORY_ENGLISH_TITLE,
              "sv_SE",
              StudyProgramImporterConstants.STUDY_PROGRAM_TAG_CATEGORY_SWEDISH_TITLE),
          StudyProgramImporterConstants.JOD_GROUP_ID);
    } else {
      tagsService.addOrUpdateJodTaxonomyCategory(
          null,
          StudyProgramImporterConstants.STUDY_PROGRAM_TAG_CATEGORY_EXTERNAL_REFERENCE_CODE,
          StudyProgramImporterConstants.STUDY_PROGRAM_TAG_CATEGORY_FINNISH_TITLE,
          Map.of(
              "fi_FI",
              StudyProgramImporterConstants.STUDY_PROGRAM_TAG_CATEGORY_FINNISH_TITLE,
              "en_US",
              StudyProgramImporterConstants.STUDY_PROGRAM_TAG_CATEGORY_ENGLISH_TITLE,
              "sv_SE",
              StudyProgramImporterConstants.STUDY_PROGRAM_TAG_CATEGORY_SWEDISH_TITLE),
          StudyProgramImporterConstants.JOD_GROUP_ID);
    }
  }

  private void initializeStudyProgramCategory() throws PortalException {

    var studyProgramVocabulary = getStudyProgramCategoryVocabulary();

    if (studyProgramVocabulary == null) {
      // Try to fetch the existing vocabulary by title
      var vocabularies =
          assetVocabularyLocalService.getCompanyVocabularies(portal.getDefaultCompanyId());

      studyProgramVocabulary =
          vocabularies.stream()
              .filter(
                  vocabulary ->
                      StudyProgramImporterConstants.STUDY_PROGRAM_CATEGORY_VOCABULARY_FINNISH_TITLE
                          .equals(vocabulary.getTitle("fi_FI")))
              .findFirst()
              .orElse(null);
    }

    if (studyProgramVocabulary == null) {
      studyProgramVocabulary = createStudyProgramVocabulary();
      log.info("Created studyProgramVocabulary: " + studyProgramVocabulary.getVocabularyId());
    }

    // Ensure the vocabulary has the correct external reference code
    studyProgramVocabulary.setExternalReferenceCode(
        StudyProgramImporterConstants.STUDY_PROGRAM_CATEGORY_VOCABULARY_EXTERNAL_REFERENCE_CODE);

    assetVocabularyLocalService.updateAssetVocabulary(studyProgramVocabulary);

    var studyProgramCategory = getStudyProgramCategory();
    var studyProgramParentCategory = getStudyProgramParentCategory();

    if (studyProgramCategory == null) {
      // Try to fetch the existing category by title
      studyProgramCategory =
          studyProgramVocabulary.getCategories().stream()
              .filter(
                  assetCategory ->
                      StudyProgramImporterConstants.STUDY_PROGRAM_CATEGORY_FINNISH_TITLE.equals(
                          assetCategory.getTitle("fi_FI")))
              .findFirst()
              .orElse(null);
    }
    if (studyProgramParentCategory == null) {
      // Try to fetch the existing parent category by title
      studyProgramParentCategory =
          studyProgramVocabulary.getCategories().stream()
              .filter(
                  assetCategory ->
                      StudyProgramImporterConstants.STUDY_PROGRAM_PARENT_CATEGORY_FINNISH_TITLE
                          .equals(assetCategory.getTitle("fi_FI")))
              .findFirst()
              .orElse(null);
    }

    if (studyProgramParentCategory == null) {
      studyProgramParentCategory =
          createStudyProgramParentCategory(studyProgramVocabulary.getVocabularyId());
    }
    // Ensure the parent category has the correct external reference code
    studyProgramParentCategory.setExternalReferenceCode(
        StudyProgramImporterConstants.STUDY_PROGRAM_PARENT_CATEGORY_EXTERNAL_REFERENCE_CODE);
    assetCategoryLocalService.updateAssetCategory(studyProgramParentCategory);

    if (studyProgramCategory == null) {
      studyProgramCategory =
          createStudyProgramCategory(
              studyProgramParentCategory.getCategoryId(), studyProgramVocabulary.getVocabularyId());
    }

    // Ensure the category has the correct external reference code
    studyProgramCategory.setExternalReferenceCode(
        StudyProgramImporterConstants.STUDY_PROGRAM_CATEGORY_EXTERNAL_REFERENCE_CODE);
    studyProgramCategory.setParentCategoryId(studyProgramParentCategory.getCategoryId());
    assetCategoryLocalService.updateAssetCategory(studyProgramCategory);
  }

  private AssetVocabulary createStudyProgramVocabulary() throws PortalException {
    log.info(
        "Creating new study program category vocabulary: "
            + StudyProgramImporterConstants.STUDY_PROGRAM_CATEGORY_VOCABULARY_FINNISH_TITLE);
    var user = GuestOrUserUtil.getGuestOrUser(portal.getDefaultCompanyId());
    var assetVocabularySettingsHelper = new AssetVocabularySettingsHelper();
    assetVocabularySettingsHelper.setMultiValued(true);
    assetVocabularySettingsHelper.setClassNameIdsAndClassTypePKs(
        new long[] {portal.getClassNameId(JournalArticle.class.getName())},
        new long[] {AssetCategoryConstants.ALL_CLASS_TYPE_PK},
        new boolean[] {false});

    return assetVocabularyLocalService.addVocabulary(
        StudyProgramImporterConstants.STUDY_PROGRAM_CATEGORY_VOCABULARY_EXTERNAL_REFERENCE_CODE,
        user.getUserId(),
        StudyProgramImporterConstants.JOD_GROUP_ID,
        "category",
        null,
        LocalizedMapUtil.getLocalizedMap(
            Map.of(
                "fi_FI",
                StudyProgramImporterConstants.STUDY_PROGRAM_CATEGORY_VOCABULARY_FINNISH_TITLE,
                "en_US",
                StudyProgramImporterConstants.STUDY_PROGRAM_CATEGORY_VOCABULARY_ENGLISH_TITLE,
                "sv_SE",
                StudyProgramImporterConstants.STUDY_PROGRAM_CATEGORY_VOCABULARY_SWEDISH_TITLE)),
        null,
        assetVocabularySettingsHelper.toString(),
        AssetVocabularyConstants.VISIBILITY_TYPE_PUBLIC,
        new ServiceContext());
  }

  private AssetCategory createStudyProgramParentCategory(Long vocabularyId) throws PortalException {
    log.info(
        "Creating new study program parent category: "
            + StudyProgramImporterConstants.STUDY_PROGRAM_PARENT_CATEGORY_FINNISH_TITLE);
    var user = GuestOrUserUtil.getGuestOrUser(portal.getDefaultCompanyId());
    return assetCategoryLocalService.addCategory(
        StudyProgramImporterConstants.STUDY_PROGRAM_PARENT_CATEGORY_EXTERNAL_REFERENCE_CODE,
        user.getUserId(),
        StudyProgramImporterConstants.JOD_GROUP_ID,
        0,
        LocalizedMapUtil.getLocalizedMap(
            Map.of(
                "fi_FI",
                StudyProgramImporterConstants.STUDY_PROGRAM_PARENT_CATEGORY_FINNISH_TITLE,
                "en_US",
                StudyProgramImporterConstants.STUDY_PROGRAM_PARENT_CATEGORY_ENGLISH_TITLE,
                "sv_SE",
                StudyProgramImporterConstants.STUDY_PROGRAM_PARENT_CATEGORY_SWEDISH_TITLE)),
        Map.of(),
        vocabularyId,
        null,
        new ServiceContext());
  }

  private AssetCategory createStudyProgramCategory(Long parentCategoryId, Long vocabularyId)
      throws PortalException {
    log.info(
        "Creating new study program category: "
            + StudyProgramImporterConstants.STUDY_PROGRAM_CATEGORY_FINNISH_TITLE);
    var user = GuestOrUserUtil.getGuestOrUser(portal.getDefaultCompanyId());
    return assetCategoryLocalService.addCategory(
        StudyProgramImporterConstants.STUDY_PROGRAM_CATEGORY_EXTERNAL_REFERENCE_CODE,
        user.getUserId(),
        StudyProgramImporterConstants.JOD_GROUP_ID,
        parentCategoryId,
        LocalizedMapUtil.getLocalizedMap(
            Map.of(
                "fi_FI",
                StudyProgramImporterConstants.STUDY_PROGRAM_CATEGORY_FINNISH_TITLE,
                "en_US",
                StudyProgramImporterConstants.STUDY_PROGRAM_CATEGORY_ENGLISH_TITLE,
                "sv_SE",
                StudyProgramImporterConstants.STUDY_PROGRAM_CATEGORY_SWEDISH_TITLE)),
        Map.of(),
        vocabularyId,
        null,
        new ServiceContext());
  }
}
