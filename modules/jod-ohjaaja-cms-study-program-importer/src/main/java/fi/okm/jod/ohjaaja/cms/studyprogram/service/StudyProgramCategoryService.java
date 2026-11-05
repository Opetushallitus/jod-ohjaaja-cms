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
import com.liferay.asset.kernel.service.AssetCategoryLocalService;
import com.liferay.journal.model.JournalArticle;
import com.liferay.journal.service.JournalArticleLocalService;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.module.framework.ModuleServiceLifecycle;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.vulcan.util.LocalizedMapUtil;
import fi.okm.jod.ohjaaja.cms.navigation.service.MainCategoryService;
import fi.okm.jod.ohjaaja.cms.studyprogram.constants.StudyProgramImporterConstants;
import fi.okm.jod.ohjaaja.cms.tags.dto.JodTaxonomyCategoryDto;
import fi.okm.jod.ohjaaja.cms.tags.service.TagsService;
import fi.okm.jod.ohjaaja.cms.util.AdminUtil;
import fi.okm.jod.ohjaaja.cms.util.JodOhjaajaCmsUtil;
import fi.okm.jod.ohjaaja.cms.util.TakeOverUtil;
import fi.okm.jod.ohjaaja.cms.util.VocabularyInitializer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = StudyProgramCategoryService.class, immediate = true)
public class StudyProgramCategoryService {

  private static final Log log = LogFactoryUtil.getLog(StudyProgramCategoryService.class);

  private static final String LANGUAGE_ID_FI_FI = "fi_FI";
  private static final String LANGUAGE_ID_EN_US = "en_US";
  private static final String LANGUAGE_ID_SV_SE = "sv_SE";

  private static final List<String> STUDY_PROGRAM_CATEGORY_TITLES =
      List.of(
          StudyProgramImporterConstants.STUDY_PROGRAM_CATEGORY_FINNISH_TITLE,
          StudyProgramImporterConstants.STUDY_PROGRAM_CATEGORY_SWEDISH_TITLE,
          StudyProgramImporterConstants.STUDY_PROGRAM_CATEGORY_ENGLISH_TITLE);

  @Reference private AssetCategoryLocalService assetCategoryLocalService;
  @Reference private JournalArticleLocalService journalArticleLocalService;
  @Reference private TagsService tagsService;
  @Reference private JodOhjaajaCmsUtil jodOhjaajaCmsUtil;
  @Reference private Portal portal;
  @Reference private MainCategoryService mainCategoryService;
  @Reference private VocabularyInitializer vocabularyInitializer;

  // The default company and its admin user must exist
  @Reference(target = ModuleServiceLifecycle.PORTAL_INITIALIZED)
  private ModuleServiceLifecycle portalInitialized;

  @Activate
  protected void activate() {
    log.info("Study Program Importer Portlet activated");
    initialize();
  }

  /**
   * Creates or takes over the study program tag and category. Run on activation and before each
   * import, so that a fixed cause of a failure, e.g. a renamed main category, does not need a
   * restart. Failures are only logged, the import checks the result.
   */
  public void initialize() {
    // Separately, so that a missing tag vocabulary does not prevent the category
    try {
      initializeStudyProgramTag();
    } catch (RuntimeException e) {
      log.error("Failed to initialize the study program tag", e);
    }
    try {
      initializeStudyProgramCategory();
    } catch (PortalException | RuntimeException e) {
      log.error("Failed to initialize the study program category", e);
    }
  }

  public void setJournalArticleCategories(JournalArticle article, long userId)
      throws PortalException {
    var categoryIds =
        new long[] {
          getStudyProgramTagCategory().id(),
          getStudyProgramCategory().getCategoryId(),
          getStudyProgramParentCategory().getCategoryId()
        };

    var currentCategoryIds =
        assetCategoryLocalService.getCategoryIds(
            JournalArticle.class.getName(), article.getResourcePrimKey());
    Arrays.sort(categoryIds);
    Arrays.sort(currentCategoryIds);
    if (Arrays.equals(categoryIds, currentCategoryIds)) {
      // Updating unchanged categories would only write and reindex the asset on every import
      return;
    }

    journalArticleLocalService.updateAsset(userId, article, categoryIds, null, null, 0.0);
  }

  /** Whether the study program tag exists, which is added to every study program. */
  public boolean hasStudyProgramTagCategory() {
    return getStudyProgramTagCategory() != null;
  }

  private JodTaxonomyCategoryDto getStudyProgramTagCategory() {
    var tagCategories =
        tagsService.getJodTaxonomyCategories(
            jodOhjaajaCmsUtil.getJodOhjaajaCmsGroup().getGroupId());

    return tagCategories.stream()
        .filter(
            tagCategory ->
                StudyProgramImporterConstants.STUDY_PROGRAM_TAG_CATEGORY_EXTERNAL_REFERENCE_CODE
                    .equals(tagCategory.externalReferenceCode()))
        .findFirst()
        .orElse(null);
  }

  /** The category of the study programs, or null if it has not been initialized. */
  public AssetCategory getStudyProgramCategory() {
    var vocabulary = mainCategoryService.fetchCategoryVocabulary();
    if (vocabulary == null) {
      return null;
    }
    var category =
        assetCategoryLocalService.fetchAssetCategoryByExternalReferenceCode(
            StudyProgramImporterConstants.STUDY_PROGRAM_CATEGORY_EXTERNAL_REFERENCE_CODE,
            vocabulary.getGroupId());
    return category != null && category.getVocabularyId() == vocabulary.getVocabularyId()
        ? category
        : null;
  }

  /** The main category of the study programs, or null if it has not been initialized. */
  public AssetCategory getStudyProgramParentCategory() {
    return mainCategoryService.fetchMainCategory(
        MainCategoryService.PROFESSIONAL_DEVELOPMENT_CATEGORY_EXTERNAL_REFERENCE_CODE);
  }

  private void initializeStudyProgramTag() {

    var tagCategory = getStudyProgramTagCategory();

    if (tagCategory != null) {
      tagsService.addOrUpdateJodTaxonomyCategory(
          tagCategory.id(),
          StudyProgramImporterConstants.STUDY_PROGRAM_TAG_CATEGORY_EXTERNAL_REFERENCE_CODE,
          StudyProgramImporterConstants.STUDY_PROGRAM_TAG_CATEGORY_FINNISH_TITLE,
          Map.of(
              LANGUAGE_ID_FI_FI,
              StudyProgramImporterConstants.STUDY_PROGRAM_TAG_CATEGORY_FINNISH_TITLE,
              LANGUAGE_ID_EN_US,
              StudyProgramImporterConstants.STUDY_PROGRAM_TAG_CATEGORY_ENGLISH_TITLE,
              LANGUAGE_ID_SV_SE,
              StudyProgramImporterConstants.STUDY_PROGRAM_TAG_CATEGORY_SWEDISH_TITLE),
          jodOhjaajaCmsUtil.getJodOhjaajaCmsGroup().getGroupId());
    } else {
      tagsService.addOrUpdateJodTaxonomyCategory(
          null,
          StudyProgramImporterConstants.STUDY_PROGRAM_TAG_CATEGORY_EXTERNAL_REFERENCE_CODE,
          StudyProgramImporterConstants.STUDY_PROGRAM_TAG_CATEGORY_FINNISH_TITLE,
          Map.of(
              LANGUAGE_ID_FI_FI,
              StudyProgramImporterConstants.STUDY_PROGRAM_TAG_CATEGORY_FINNISH_TITLE,
              LANGUAGE_ID_EN_US,
              StudyProgramImporterConstants.STUDY_PROGRAM_TAG_CATEGORY_ENGLISH_TITLE,
              LANGUAGE_ID_SV_SE,
              StudyProgramImporterConstants.STUDY_PROGRAM_TAG_CATEGORY_SWEDISH_TITLE),
          jodOhjaajaCmsUtil.getJodOhjaajaCmsGroup().getGroupId());
    }
  }

  // The vocabulary and the main category are managed by the navigation, the study program
  // category under the main category here
  private void initializeStudyProgramCategory() throws PortalException {
    var parentCategory =
        mainCategoryService.initMainCategories().stream()
            .filter(
                mainCategory ->
                    MainCategoryService.PROFESSIONAL_DEVELOPMENT_CATEGORY_EXTERNAL_REFERENCE_CODE
                        .equals(mainCategory.getExternalReferenceCode()))
            .findFirst()
            .orElseThrow(
                () ->
                    new PortalException(
                        "Main category "
                            + MainCategoryService
                                .PROFESSIONAL_DEVELOPMENT_CATEGORY_EXTERNAL_REFERENCE_CODE
                            + " not found, see the log of the main categories"));
    var vocabulary = mainCategoryService.fetchCategoryVocabulary();

    var studyProgramCategory = getStudyProgramCategory();
    if (studyProgramCategory == null) {
      // Try to fetch the existing category by title
      studyProgramCategory =
          vocabulary.getCategories().stream()
              .filter(
                  assetCategory ->
                      TakeOverUtil.hasAnyTitle(
                          assetCategory.getTitleMap(), STUDY_PROGRAM_CATEGORY_TITLES))
              .findFirst()
              .orElse(null);
    }

    if (studyProgramCategory == null) {
      createStudyProgramCategory(
          parentCategory.getCategoryId(), vocabulary.getVocabularyId(), vocabulary.getGroupId());
      return;
    }

    if (!StudyProgramImporterConstants.STUDY_PROGRAM_CATEGORY_EXTERNAL_REFERENCE_CODE.equals(
        studyProgramCategory.getExternalReferenceCode())) {
      studyProgramCategory.setExternalReferenceCode(
          StudyProgramImporterConstants.STUDY_PROGRAM_CATEGORY_EXTERNAL_REFERENCE_CODE);
      studyProgramCategory = assetCategoryLocalService.updateAssetCategory(studyProgramCategory);
      log.info("Took over study program category " + studyProgramCategory.getCategoryId());
    }
    // moveCategory also updates the tree path, which the category queries use
    if (studyProgramCategory.getParentCategoryId() != parentCategory.getCategoryId()) {
      assetCategoryLocalService.moveCategory(
          studyProgramCategory.getCategoryId(),
          parentCategory.getCategoryId(),
          vocabulary.getVocabularyId(),
          new ServiceContext());
      log.info("Moved study program category under " + parentCategory.getExternalReferenceCode());
    }
    vocabularyInitializer.ensureGuestViewPermission(
        studyProgramCategory.getCompanyId(),
        AssetCategory.class.getName(),
        studyProgramCategory.getCategoryId());
  }

  private AssetCategory createStudyProgramCategory(
      long parentCategoryId, long vocabularyId, long groupId) throws PortalException {
    log.info(
        "Creating new study program category: "
            + StudyProgramImporterConstants.STUDY_PROGRAM_CATEGORY_FINNISH_TITLE);
    var user = AdminUtil.getAdminUser(portal.getDefaultCompanyId());
    var serviceContext = new ServiceContext();
    serviceContext.setAddGroupPermissions(true);
    serviceContext.setAddGuestPermissions(true);
    return assetCategoryLocalService.addCategory(
        StudyProgramImporterConstants.STUDY_PROGRAM_CATEGORY_EXTERNAL_REFERENCE_CODE,
        user.getUserId(),
        groupId,
        parentCategoryId,
        LocalizedMapUtil.getLocalizedMap(
            Map.of(
                LANGUAGE_ID_FI_FI,
                StudyProgramImporterConstants.STUDY_PROGRAM_CATEGORY_FINNISH_TITLE,
                LANGUAGE_ID_EN_US,
                StudyProgramImporterConstants.STUDY_PROGRAM_CATEGORY_ENGLISH_TITLE,
                LANGUAGE_ID_SV_SE,
                StudyProgramImporterConstants.STUDY_PROGRAM_CATEGORY_SWEDISH_TITLE)),
        Map.of(),
        vocabularyId,
        null,
        serviceContext);
  }
}
