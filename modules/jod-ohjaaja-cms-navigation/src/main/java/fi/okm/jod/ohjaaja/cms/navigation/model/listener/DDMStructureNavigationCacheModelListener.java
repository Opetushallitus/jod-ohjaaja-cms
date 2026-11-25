/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.navigation.model.listener;

import com.liferay.dynamic.data.mapping.model.DDMStructure;
import com.liferay.portal.kernel.model.ModelListener;
import fi.okm.jod.ohjaaja.cms.navigation.service.NavigationService;
import org.osgi.service.component.annotations.Component;

@Component(service = ModelListener.class)
public class DDMStructureNavigationCacheModelListener
    extends BaseNavigationCacheModelListener<DDMStructure> {

  // The IDs of the content structures are in the navigation, e.g. a structure created or taken over
  @Override
  protected boolean affectsNavigation(DDMStructure structure) {
    return NavigationService.CONTENT_STRUCTURE_EXTERNAL_REFERENCE_CODES.contains(
        structure.getExternalReferenceCode());
  }
}
