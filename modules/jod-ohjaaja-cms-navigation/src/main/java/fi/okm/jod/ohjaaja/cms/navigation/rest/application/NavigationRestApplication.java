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
import java.util.Collections;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.jaxrs.whiteboard.JaxrsWhiteboardConstants;

@Component(
    property = {
      JaxrsWhiteboardConstants.JAX_RS_APPLICATION_BASE + "=/jod-navigation",
      JaxrsWhiteboardConstants.JAX_RS_NAME + "=Navigation.Rest",
    },
    service = Application.class,
    immediate = true)
public class NavigationRestApplication extends Application {

  @Reference private NavigationService navigationService;

  @Override
  public Set<Object> getSingletons() {
    return Collections.singleton(this);
  }

  @GET
  @Path("/{siteId}")
  @Produces("application/json")
  public Response navigation(
      @Context HttpServletRequest request, @PathParam("siteId") Long siteId) {
    String languageId = request.getLocale().toLanguageTag();
    return Response.ok().entity(navigationService.getNavigation(siteId, languageId)).build();
  }

  @Activate
  protected void activate() {
    navigationService.initNavigation();
  }
}
