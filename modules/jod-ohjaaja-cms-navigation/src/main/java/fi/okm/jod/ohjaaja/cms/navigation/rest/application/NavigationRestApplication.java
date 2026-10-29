/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.navigation.rest.application;

import fi.okm.jod.ohjaaja.cms.navigation.service.NavigationService;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Response;
import java.util.Collections;
import java.util.Set;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.jaxrs.whiteboard.JaxrsWhiteboardConstants;

@Component(
    immediate = true,
    property = {
      JaxrsWhiteboardConstants.JAX_RS_APPLICATION_BASE + "=/jod-navigation",
      JaxrsWhiteboardConstants.JAX_RS_NAME + "=Navigation.Rest"
    },
    service = Application.class)
public class NavigationRestApplication extends Application {

  @Reference private NavigationService navigationService;

  @Override
  public Set<Object> getSingletons() {
    return Collections.singleton(this);
  }

  @GET
  @Produces("application/json")
  public Response navigation() {
    return Response.ok().entity(navigationService.getNavigation()).build();
  }

  /**
   * The previous path of the navigation, kept until the UI uses the path without the site ID.
   *
   * @deprecated use {@link #navigation()}, the site ID is ignored
   */
  @Deprecated(forRemoval = true)
  @GET
  @Path("/{siteId}")
  @Produces("application/json")
  public Response navigationBySiteId() {
    return navigation();
  }

  @Activate
  protected void activate() {
    navigationService.initNavigation();
  }
}
