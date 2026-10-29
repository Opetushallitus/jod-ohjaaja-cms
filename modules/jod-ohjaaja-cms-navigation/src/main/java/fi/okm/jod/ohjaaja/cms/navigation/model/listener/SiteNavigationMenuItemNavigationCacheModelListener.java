/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.navigation.model.listener;

import com.liferay.portal.kernel.model.ModelListener;
import com.liferay.site.navigation.model.SiteNavigationMenuItem;
import org.osgi.service.component.annotations.Component;

@Component(service = ModelListener.class)
public class SiteNavigationMenuItemNavigationCacheModelListener
    extends BaseNavigationCacheModelListener<SiteNavigationMenuItem> {}
