/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.statistics.application.list;

import com.liferay.application.list.BasePanelApp;
import com.liferay.application.list.PanelApp;
import com.liferay.portal.kernel.model.Portlet;
import fi.okm.jod.ohjaaja.cms.statistics.constants.StatisticsPortletKeys;
import fi.okm.jod.ohjaaja.cms.studyprogram.constants.OhjaajaPanelCategoryKeys;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(
    property = {
      "panel.app.order:Integer=1400",
      "panel.category.key=" + OhjaajaPanelCategoryKeys.OHJAAJA_PANEL_CATEGORY_KEY
    },
    service = PanelApp.class)
public class StatisticsPanelApp extends BasePanelApp {
  @Reference(target = "(jakarta.portlet.name=" + StatisticsPortletKeys.STATISTICS + ")")
  private Portlet portlet;

  @Override
  public Portlet getPortlet() {
    return portlet;
  }

  @Override
  public String getPortletId() {
    return StatisticsPortletKeys.STATISTICS;
  }
}
