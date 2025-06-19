/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.studyprogram.application.list;

import com.liferay.application.list.BasePanelApp;
import com.liferay.application.list.PanelApp;
import com.liferay.portal.kernel.model.Portlet;
import fi.okm.jod.ohjaaja.cms.studyprogram.constants.OhjaajaPanelCategoryKeys;
import fi.okm.jod.ohjaaja.cms.studyprogram.constants.StudyProgramImporterPortletKeys;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(
    property = {
      "panel.app.order:Integer=1200",
      "panel.category.key=" + OhjaajaPanelCategoryKeys.OHJAAJA_PANEL_CATEGORY_KEY
    },
    service = PanelApp.class)
public class StudyProgramImporterPanelApp extends BasePanelApp {

  @Reference(
      target =
          "(javax.portlet.name=" + StudyProgramImporterPortletKeys.STUDY_PROGRAM_IMPORTER + ")")
  private Portlet portlet;

  @Override
  public Portlet getPortlet() {
    return portlet;
  }

  @Override
  public String getPortletId() {
    return StudyProgramImporterPortletKeys.STUDY_PROGRAM_IMPORTER;
  }
}
