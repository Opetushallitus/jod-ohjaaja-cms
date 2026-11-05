/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.navigation.service;

import com.liferay.asset.kernel.model.AssetCategory;
import com.liferay.asset.kernel.model.AssetVocabulary;
import com.liferay.portal.kernel.exception.PortalException;
import java.util.List;

/**
 * The category vocabulary of the site and its three main categories, which are also the top level
 * of the navigation in this order.
 *
 * <p>Missing ones are created. Existing ones, also those created by hand, are only given their
 * external reference code and can otherwise be edited in the UI.
 */
public interface MainCategoryService {

  String CATEGORY_VOCABULARY_EXTERNAL_REFERENCE_CODE = "jod-ohjaaja-cms-categories";

  String INFORMATION_CONTENT_CATEGORY_EXTERNAL_REFERENCE_CODE =
      "jod-ohjaaja-cms-category-information-content";

  String CUSTOMER_WORK_CATEGORY_EXTERNAL_REFERENCE_CODE = "jod-ohjaaja-cms-category-customer-work";

  String PROFESSIONAL_DEVELOPMENT_CATEGORY_EXTERNAL_REFERENCE_CODE =
      "jod-ohjaaja-cms-category-professional-development";

  /** Creates or takes over the vocabulary and the main categories, which are returned in order. */
  List<AssetCategory> initMainCategories() throws PortalException;

  /** The category vocabulary, or null if it has not been initialized. */
  AssetVocabulary fetchCategoryVocabulary();

  /** The main category with the given external reference code, or null. */
  AssetCategory fetchMainCategory(String externalReferenceCode);
}
