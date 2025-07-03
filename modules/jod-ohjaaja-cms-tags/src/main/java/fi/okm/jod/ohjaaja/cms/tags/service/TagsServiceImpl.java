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

import com.liferay.asset.kernel.service.AssetCategoryLocalService;
import com.liferay.asset.kernel.service.AssetVocabularyLocalService;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.security.auth.GuestOrUserUtil;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.vulcan.util.LocalizedMapUtil;
import fi.okm.jod.ohjaaja.cms.tags.dto.JodCategoryType;
import fi.okm.jod.ohjaaja.cms.tags.dto.JodTaxonomyCategoryDto;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(immediate = true, service = TagsService.class)
public class TagsServiceImpl implements TagsService {

  @Reference private AssetVocabularyLocalService assetVocabularyLocalService;
  @Reference private AssetCategoryLocalService assetCategoryLocalService;

  @Override
  public List<JodTaxonomyCategoryDto> getJodTaxonomyCategories(Long siteId) {
    try {
      var tagVocabulary =
          assetVocabularyLocalService.getAssetVocabularyByExternalReferenceCode(
              JOD_TAG_VOCABULARY_EXTERNAL_REFERENCE_CODE, siteId);

      return tagVocabulary.getCategories().stream()
          .map(
              assetCategory ->
                  new JodTaxonomyCategoryDto(
                      assetCategory.getCategoryId(),
                      assetCategory.getExternalReferenceCode(),
                      assetCategory.getName(),
                      LocalizedMapUtil.getI18nMap(assetCategory.getTitleMap()),
                      JodCategoryType.TAG))
          .collect(Collectors.toList());

    } catch (PortalException e) {
      return null;
    }
  }

  @Override
  public void addOrUpdateJodTaxonomyCategory(
      Long categoryId,
      String externalReferenceCode,
      String name,
      Map<String, String> name_i18n,
      Long siteId) {
    try {
      var tagVocabulary =
          assetVocabularyLocalService.getAssetVocabularyByExternalReferenceCode(
              JOD_TAG_VOCABULARY_EXTERNAL_REFERENCE_CODE, siteId);

      var user = GuestOrUserUtil.getGuestOrUser(PortalUtil.getDefaultCompanyId());
      if (categoryId == null) {
        // Create new category
        assetCategoryLocalService.addCategory(
            externalReferenceCode,
            user.getUserId(),
            siteId,
            0,
            LocalizedMapUtil.getLocalizedMap(name_i18n),
            Map.of(),
            tagVocabulary.getVocabularyId(),
            null,
            new ServiceContext());

      } else {
        // Update existing category
        var assetCategory = assetCategoryLocalService.getAssetCategory(categoryId);
        assetCategory.setExternalReferenceCode(externalReferenceCode);
        assetCategory.setName(name);
        assetCategory.setTitleMap(LocalizedMapUtil.getLocalizedMap(name_i18n));
        assetCategoryLocalService.updateAssetCategory(assetCategory);
      }
    } catch (PortalException e) {
      throw new RuntimeException("Failed to add or update taxonomy category", e);
    }
  }
}
