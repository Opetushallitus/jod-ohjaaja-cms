/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.tags.service;

import static fi.okm.jod.ohjaaja.cms.tags.Constants.JOD_TAG_VOCABULARY_EXTERNAL_REFERENCE_CODE;

import com.liferay.asset.kernel.model.AssetVocabulary;
import com.liferay.asset.kernel.service.AssetCategoryLocalService;
import com.liferay.asset.kernel.service.AssetVocabularyLocalService;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.module.framework.ModuleServiceLifecycle;
import com.liferay.portal.kernel.security.auth.GuestOrUserUtil;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.vulcan.util.LocalizedMapUtil;
import fi.okm.jod.ohjaaja.cms.tags.dto.JodCategoryType;
import fi.okm.jod.ohjaaja.cms.tags.dto.JodTaxonomyCategoryDto;
import fi.okm.jod.ohjaaja.cms.tags.exception.TagsServiceException;
import fi.okm.jod.ohjaaja.cms.util.VocabularyInitializer;
import java.util.List;
import java.util.Map;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(immediate = true, service = TagsService.class)
public class TagsServiceImpl implements TagsService {

  private static final Log log = LogFactoryUtil.getLog(TagsServiceImpl.class);

  private static final String TAG_VOCABULARY_NAME = "tags";
  private static final Map<String, String> TAG_VOCABULARY_TITLES =
      Map.of("fi_FI", "Avainsanat", "en_US", "Tags", "sv_SE", "Taggar");

  @Reference private AssetVocabularyLocalService assetVocabularyLocalService;
  @Reference private AssetCategoryLocalService assetCategoryLocalService;
  @Reference private VocabularyInitializer vocabularyInitializer;

  // The default company and its admin user must exist
  @Reference(target = ModuleServiceLifecycle.PORTAL_INITIALIZED)
  private ModuleServiceLifecycle portalInitialized;

  // The service is registered only after the activation, so the users of the service, such as the
  // study program importer adding its tag, find the vocabulary. A failure is only logged: the tags
  // API then returns no tags and adding a tag fails with an error naming the missing vocabulary.
  @Activate
  protected void activate() {
    try {
      initTagVocabulary();
    } catch (RuntimeException e) {
      log.error(e.getMessage(), e);
    }
  }

  @Override
  public void initTagVocabulary() {
    try {
      vocabularyInitializer.getOrCreateVocabulary(
          JOD_TAG_VOCABULARY_EXTERNAL_REFERENCE_CODE,
          null,
          TAG_VOCABULARY_NAME,
          TAG_VOCABULARY_TITLES);
    } catch (PortalException e) {
      throw new TagsServiceException(
          "Failed to initialize the tag vocabulary " + JOD_TAG_VOCABULARY_EXTERNAL_REFERENCE_CODE,
          e);
    }
  }

  @Override
  public List<JodTaxonomyCategoryDto> getJodTaxonomyCategories(Long siteId) {
    var tagVocabulary = fetchTagVocabulary(siteId);
    if (tagVocabulary == null) {
      log.warn(
          "Tag vocabulary "
              + JOD_TAG_VOCABULARY_EXTERNAL_REFERENCE_CODE
              + " not found in site "
              + siteId
              + ", returning no tags");
      return List.of();
    }

    return tagVocabulary.getCategories().stream()
        .map(
            assetCategory ->
                new JodTaxonomyCategoryDto(
                    assetCategory.getCategoryId(),
                    assetCategory.getExternalReferenceCode(),
                    assetCategory.getName(),
                    LocalizedMapUtil.getI18nMap(assetCategory.getTitleMap()),
                    JodCategoryType.TAG))
        .toList();
  }

  @Override
  public void addOrUpdateJodTaxonomyCategory(
      Long categoryId,
      String externalReferenceCode,
      String name,
      Map<String, String> nameI18n,
      Long siteId) {
    var tagVocabulary = fetchTagVocabulary(siteId);
    if (tagVocabulary == null) {
      throw new TagsServiceException(
          "Failed to add or update taxonomy category "
              + externalReferenceCode
              + ": tag vocabulary "
              + JOD_TAG_VOCABULARY_EXTERNAL_REFERENCE_CODE
              + " not found in site "
              + siteId,
          null);
    }

    try {
      var user = GuestOrUserUtil.getGuestOrUser(PortalUtil.getDefaultCompanyId());
      if (categoryId == null) {
        var assetCategory =
            assetCategoryLocalService.addCategory(
                externalReferenceCode,
                user.getUserId(),
                siteId,
                0,
                LocalizedMapUtil.getLocalizedMap(nameI18n),
                Map.of(),
                tagVocabulary.getVocabularyId(),
                false,
                null,
                new ServiceContext());
        assetCategoryLocalService.addCategoryResources(assetCategory, true, true);
      } else {
        var assetCategory = assetCategoryLocalService.getAssetCategory(categoryId);
        assetCategory.setExternalReferenceCode(externalReferenceCode);
        assetCategory.setName(name);
        assetCategory.setTitleMap(LocalizedMapUtil.getLocalizedMap(nameI18n));
        assetCategoryLocalService.updateAssetCategory(assetCategory);
        assetCategoryLocalService.addCategoryResources(assetCategory, true, true);
      }
    } catch (PortalException e) {
      throw new TagsServiceException("Failed to add or update taxonomy category", e);
    }
  }

  private AssetVocabulary fetchTagVocabulary(Long siteId) {
    return siteId == null
        ? null
        : assetVocabularyLocalService.fetchAssetVocabularyByExternalReferenceCode(
            JOD_TAG_VOCABULARY_EXTERNAL_REFERENCE_CODE, siteId);
  }
}
