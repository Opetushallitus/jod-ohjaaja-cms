/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.navigation.service;

import com.liferay.journal.model.JournalArticle;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.site.navigation.model.SiteNavigationMenuItem;
import fi.okm.jod.ohjaaja.cms.navigation.dto.NavigationDto;
import fi.okm.jod.ohjaaja.cms.navigation.exception.MultipleStudyProgramListingMenuItemExpection;
import fi.okm.jod.ohjaaja.cms.navigation.exception.StudyProgramListingMissingException;

public interface NavigationService {
  void addOrUpdateStudyProgramNavigationMenuItem(
      JournalArticle studyProgramJournalArticle, ServiceContext serviceContext)
      throws StudyProgramListingMissingException, MultipleStudyProgramListingMenuItemExpection;

  void deleteStudyProgramNavigationMenuItem(String externalReferenceCode) throws PortalException;

  SiteNavigationMenuItem getStudyProgramsParentMenuItem()
      throws StudyProgramListingMissingException, MultipleStudyProgramListingMenuItemExpection;

  NavigationDto getNavigation(Long siteId, String languageId);

  void initNavigation();
}
