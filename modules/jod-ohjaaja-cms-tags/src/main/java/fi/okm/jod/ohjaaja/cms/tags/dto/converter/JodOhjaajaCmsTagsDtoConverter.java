/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.tags.dto.converter;

import static fi.okm.jod.ohjaaja.cms.tags.Constants.JOD_TAG_VOCABULARY_EXTERNAL_REFERENCE_CODE;

import com.liferay.asset.kernel.model.AssetCategory;
import com.liferay.asset.kernel.service.AssetCategoryLocalService;
import com.liferay.asset.kernel.service.AssetVocabularyLocalService;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.vulcan.dto.converter.DTOConverter;
import com.liferay.portal.vulcan.dto.converter.DTOConverterContext;
import com.liferay.portal.vulcan.util.LocalizedMapUtil;
import fi.okm.jod.ohjaaja.cms.tags.dto.JodCategoryType;
import fi.okm.jod.ohjaaja.cms.tags.dto.JodTaxonomyCategoryDto;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(
    property = {
      "application.name=Liferay.Headless.Admin.Taxonomy",
      "dto.class.name=com.liferay.asset.kernel.model.AssetCategory",
      "version=v1.0",
      "service.ranking:Integer=100"
    },
    immediate = true,
    service = DTOConverter.class)
public class JodOhjaajaCmsTagsDtoConverter
    implements DTOConverter<AssetCategory, JodTaxonomyCategoryDto> {

  @Reference private AssetCategoryLocalService assetCategoryLocalService;

  @Reference private AssetVocabularyLocalService assetVocabularyLocalService;

  @Override
  public String getContentType() {
    return JodTaxonomyCategoryDto.class.getName();
  }

  @Override
  public JodTaxonomyCategoryDto toDTO(DTOConverterContext dtoConverterContext) throws Exception {
    var assetCategory =
        assetCategoryLocalService.getAssetCategory((Long) dtoConverterContext.getId());
    return toJodTaxonomyCategory(assetCategory);
  }

  @Override
  public JodTaxonomyCategoryDto toDTO(
      DTOConverterContext dtoConverterContext, AssetCategory assetCategory) {
    return toJodTaxonomyCategory(assetCategory);
  }

  @Override
  public JodTaxonomyCategoryDto toDTO(AssetCategory assetCategory) {
    return toJodTaxonomyCategory(assetCategory);
  }

  private JodTaxonomyCategoryDto toJodTaxonomyCategory(AssetCategory assetCategory) {
    try {
      if (assetCategory.getVocabularyId() == 0) {
        return null;
      }

      var assetVocabulary =
          assetVocabularyLocalService.getAssetVocabulary(assetCategory.getVocabularyId());

      if (assetVocabulary == null) {
        return null;
      }

      return new JodTaxonomyCategoryDto(
          assetCategory.getCategoryId(),
          assetCategory.getExternalReferenceCode(),
          assetCategory.getName(),
          LocalizedMapUtil.getI18nMap(assetCategory.getTitleMap()),
          JOD_TAG_VOCABULARY_EXTERNAL_REFERENCE_CODE.equals(
                  assetVocabulary.getExternalReferenceCode())
              ? JodCategoryType.TAG
              : JodCategoryType.CATEGORY);

    } catch (PortalException portalException) {
      return null;
    }
  }
}
