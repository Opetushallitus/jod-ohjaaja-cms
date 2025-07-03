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
import com.liferay.asset.kernel.model.AssetCategoryConstants;
import com.liferay.asset.kernel.model.AssetVocabulary;
import com.liferay.asset.kernel.model.AssetVocabularyConstants;
import com.liferay.asset.kernel.service.AssetCategoryLocalService;
import com.liferay.asset.kernel.service.AssetVocabularyLocalService;
import com.liferay.journal.model.JournalArticle;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.security.auth.GuestOrUserUtil;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.vulcan.dto.converter.DTOConverter;
import com.liferay.portal.vulcan.dto.converter.DTOConverterContext;
import com.liferay.portal.vulcan.util.LocalizedMapUtil;
import com.liferay.portlet.asset.util.AssetVocabularySettingsHelper;
import fi.okm.jod.ohjaaja.cms.tags.dto.JodCategoryType;
import fi.okm.jod.ohjaaja.cms.tags.dto.JodTaxonomyCategoryDto;
import java.util.Locale;
import java.util.Map;
import org.osgi.service.component.annotations.Activate;
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

  @Reference private Portal portal;

  private static final Log log = LogFactoryUtil.getLog(JodOhjaajaCmsTagsDtoConverter.class);

  private static final long JOD_GROUP_ID = 20117;

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

  @Activate
  protected void activate() {
    AssetVocabulary tagVocabulary;
    try {
      tagVocabulary =
          assetVocabularyLocalService.getAssetVocabularyByExternalReferenceCode(
              JOD_TAG_VOCABULARY_EXTERNAL_REFERENCE_CODE, JOD_GROUP_ID);
    } catch (PortalException e) {
      log.info(
          "No tag vocabulary found for JodTaxonomyCategory with external reference code "
              + JOD_TAG_VOCABULARY_EXTERNAL_REFERENCE_CODE);
      tagVocabulary = null;
    }

    if (tagVocabulary == null) {
      log.info(
          "Creating tag vocabulary for JodTaxonomyCategory with external reference code "
              + JOD_TAG_VOCABULARY_EXTERNAL_REFERENCE_CODE);
      try {
        Map<Locale, String> titleMap =
            Map.of(
                LocaleUtil.fromLanguageId("en_US"),
                "Tags",
                LocaleUtil.fromLanguageId("fi_FI"),
                "Avainsanat",
                LocaleUtil.fromLanguageId("sv_SE"),
                "Taggar");

        var assetVocabularySettingsHelper = new AssetVocabularySettingsHelper();

        assetVocabularySettingsHelper.setMultiValued(true);
        assetVocabularySettingsHelper.setClassNameIdsAndClassTypePKs(
            new long[] {portal.getClassNameId(JournalArticle.class.getName())},
            new long[] {AssetCategoryConstants.ALL_CLASS_TYPE_PK},
            new boolean[] {false});

        var user = GuestOrUserUtil.getGuestOrUser(PortalUtil.getDefaultCompanyId());
        tagVocabulary =
            assetVocabularyLocalService.addVocabulary(
                JOD_TAG_VOCABULARY_EXTERNAL_REFERENCE_CODE,
                user.getUserId(),
                JOD_GROUP_ID,
                "tags",
                null,
                titleMap,
                null,
                assetVocabularySettingsHelper.toString(),
                AssetVocabularyConstants.VISIBILITY_TYPE_PUBLIC,
                new ServiceContext());
      } catch (PortalException e) {
        throw new RuntimeException(e);
      }
    }

    log.info(
        "Found tag vocabulary for JodTaxonomyCategory with external reference code "
            + JOD_TAG_VOCABULARY_EXTERNAL_REFERENCE_CODE
            + ": "
            + tagVocabulary.getName());
  }
}
