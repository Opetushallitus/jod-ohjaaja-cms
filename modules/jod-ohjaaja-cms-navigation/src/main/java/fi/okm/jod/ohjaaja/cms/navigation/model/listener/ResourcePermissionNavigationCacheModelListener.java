/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.navigation.model.listener;

import com.liferay.asset.kernel.model.AssetCategory;
import com.liferay.asset.kernel.model.AssetVocabulary;
import com.liferay.journal.model.JournalArticle;
import com.liferay.journal.model.JournalFolder;
import com.liferay.portal.kernel.model.ModelListener;
import com.liferay.portal.kernel.model.ResourcePermission;
import com.liferay.site.navigation.model.SiteNavigationMenu;
import java.util.Set;
import org.osgi.service.component.annotations.Component;

@Component(service = ModelListener.class)
public class ResourcePermissionNavigationCacheModelListener
    extends BaseNavigationCacheModelListener<ResourcePermission> {

  private static final Set<String> VIEW_PERMISSION_CHECKED_MODELS =
      Set.of(
          AssetCategory.class.getName(),
          AssetVocabulary.class.getName(),
          JournalArticle.class.getName(),
          JournalFolder.class.getName(),
          SiteNavigationMenu.class.getName());

  // The navigation only shows the menus, categories and articles the guest is permitted to view,
  // the articles also inherit the view permission of their folder
  @Override
  protected boolean affectsNavigation(ResourcePermission resourcePermission) {
    return VIEW_PERMISSION_CHECKED_MODELS.contains(resourcePermission.getName());
  }
}
