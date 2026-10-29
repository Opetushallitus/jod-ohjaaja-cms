/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.navigation.model.listener;

import com.liferay.portal.kernel.model.BaseModel;
import com.liferay.portal.kernel.model.BaseModelListener;
import fi.okm.jod.ohjaaja.cms.navigation.service.NavigationService;
import org.osgi.service.component.annotations.Reference;

/** Clears the cached navigation when a model used in the navigation changes. */
public abstract class BaseNavigationCacheModelListener<T extends BaseModel<T>>
    extends BaseModelListener<T> {

  // Injected into the subclass components, see -dsannotations-options in bnd.bnd. Not private,
  // because the component runtime cannot set a private field of a superclass.
  @Reference protected NavigationService navigationService;

  @Override
  public void onAfterCreate(T model) {
    clearNavigationCache(model);
  }

  @Override
  public void onAfterRemove(T model) {
    clearNavigationCache(model);
  }

  @Override
  public void onAfterUpdate(T originalModel, T model) {
    clearNavigationCache(model);
  }

  /** Whether a change to the model can affect the navigation. */
  protected boolean affectsNavigation(T model) {
    return true;
  }

  private void clearNavigationCache(T model) {
    if (model != null && affectsNavigation(model)) {
      navigationService.clearNavigationCache();
    }
  }
}
