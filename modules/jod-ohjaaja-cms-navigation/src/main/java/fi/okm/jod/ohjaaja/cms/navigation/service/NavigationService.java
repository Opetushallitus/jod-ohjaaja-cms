/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.navigation.service;

import com.liferay.asset.kernel.model.AssetCategory;
import com.liferay.journal.model.JournalArticle;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.site.navigation.model.SiteNavigationMenuItem;
import fi.okm.jod.ohjaaja.cms.navigation.dto.NavigationDto;
import fi.okm.jod.ohjaaja.cms.navigation.exception.MultipleStudyProgramListingMenuItemExpection;
import fi.okm.jod.ohjaaja.cms.navigation.exception.NavigationMenuItemPersistenceException;
import fi.okm.jod.ohjaaja.cms.navigation.exception.StudyProgramListingMissingException;

public interface NavigationService {

  /** The navigation menu of the site, used by the public UI. */
  String NAVIGATION_MENU_EXTERNAL_REFERENCE_CODE = "jod-ohjaaja-cms-navigation";

  /** The menu item the study programs are added under, if it is created by the importer. */
  String STUDY_PROGRAMS_LISTING_MENU_ITEM_EXTERNAL_REFERENCE_CODE =
      "jod-ohjaaja-cms-study-programs-listing";

  void addOrUpdateStudyProgramNavigationMenuItem(
      JournalArticle studyProgramJournalArticle, ServiceContext serviceContext)
      throws StudyProgramListingMissingException, NavigationMenuItemPersistenceException;

  void deleteStudyProgramNavigationMenuItem(String externalReferenceCode) throws PortalException;

  /** The menu item the study programs are added under, by its external reference code. */
  SiteNavigationMenuItem getStudyProgramsParentMenuItem()
      throws StudyProgramListingMissingException;

  /**
   * Returns the menu item the study programs are added under, found by its external reference code.
   * A menu item of type StudyProgramsListing created by hand is taken over. A missing one is
   * created for the study programs category under the menu item of the main category, which is
   * created or taken over too. Fails if the menu item is not in the navigation menu or its type was
   * changed.
   */
  SiteNavigationMenuItem getOrCreateStudyProgramsParentMenuItem(
      AssetCategory mainCategory, AssetCategory studyProgramsCategory)
      throws MultipleStudyProgramListingMenuItemExpection, NavigationMenuItemPersistenceException;

  /** Returns the navigation of the JOD Ohjaaja site as seen by a guest. */
  NavigationDto getNavigation();

  void clearNavigationCache();

  /**
   * Creates the custom fields of the menu items, the navigation menu and the menu items of the main
   * categories (see {@link MainCategoryService}). A menu created by hand before the menu had an
   * external reference code is taken over instead of creating another one. A menu item is created
   * with the external reference code of its category.
   */
  void initNavigation();
}
