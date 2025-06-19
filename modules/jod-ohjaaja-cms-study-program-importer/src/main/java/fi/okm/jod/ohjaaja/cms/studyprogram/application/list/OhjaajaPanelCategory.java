/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.studyprogram.application.list;

import com.liferay.application.list.BasePanelCategory;
import com.liferay.application.list.PanelCategory;
import com.liferay.application.list.constants.PanelCategoryKeys;
import com.liferay.portal.kernel.language.Language;
import fi.okm.jod.ohjaaja.cms.studyprogram.constants.OhjaajaPanelCategoryKeys;
import java.util.Locale;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(
    property = {
      "panel.category.key=" + PanelCategoryKeys.SITE_ADMINISTRATION,
      "panel.category.order:Integer=650"
    },
    service = PanelCategory.class)
public class OhjaajaPanelCategory extends BasePanelCategory {

  @Reference private Language language;

  @Override
  public String getKey() {
    return OhjaajaPanelCategoryKeys.OHJAAJA_PANEL_CATEGORY_KEY;
  }

  @Override
  public String getLabel(Locale locale) {
    return language.get(locale, "Ohjaaja");
  }
}
