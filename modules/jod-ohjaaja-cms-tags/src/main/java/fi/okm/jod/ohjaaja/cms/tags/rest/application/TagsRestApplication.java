/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.tags.rest.application;

import fi.okm.jod.ohjaaja.cms.tags.service.TagsService;
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
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.jaxrs.whiteboard.JaxrsWhiteboardConstants;

@Component(
    property = {
      JaxrsWhiteboardConstants.JAX_RS_APPLICATION_BASE + "=/jod-tags",
      JaxrsWhiteboardConstants.JAX_RS_NAME + "=Tags.Rest",
    },
    service = Application.class,
    immediate = true)
public class TagsRestApplication extends Application {

  @Reference private TagsService tagsService;

  @Override
  public Set<Object> getSingletons() {
    return Collections.singleton(this);
  }

  @GET
  @Path("/{siteId}")
  @Produces("application/json")
  public Response tags(@Context HttpServletRequest request, @PathParam("siteId") Long siteId) {
    return Response.ok().entity(tagsService.getJodTaxonomyCategories(siteId)).build();
  }
}
