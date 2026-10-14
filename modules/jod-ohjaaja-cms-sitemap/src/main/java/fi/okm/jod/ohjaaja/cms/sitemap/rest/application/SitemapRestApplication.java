/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.sitemap.rest.application;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import fi.okm.jod.ohjaaja.cms.sitemap.service.SitemapService;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import java.util.Collections;
import java.util.Set;
import java.util.function.Supplier;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.jaxrs.whiteboard.JaxrsWhiteboardConstants;

/** Public sitemap endpoints. The site is always the JOD Ohjaaja CMS site. */
@Component(
    immediate = true,
    property = {
      JaxrsWhiteboardConstants.JAX_RS_APPLICATION_BASE + "=/jod-sitemap",
      JaxrsWhiteboardConstants.JAX_RS_NAME + "=Sitemap.Rest"
    },
    service = Application.class)
public class SitemapRestApplication extends Application {

  private static final String XML = "application/xml; charset=UTF-8";
  private static final String HTML = "text/html; charset=UTF-8";
  private static final String TEXT = "text/plain; charset=UTF-8";
  private static final String CACHE_CONTROL = "public, max-age=1800";

  private static final Log log = LogFactoryUtil.getLog(SitemapRestApplication.class);

  @Reference private SitemapService sitemapService;

  @Override
  public Set<Object> getSingletons() {
    return Collections.singleton(this);
  }

  @GET
  @Path("/sitemap.xml")
  @Produces(XML)
  public Response sitemap() {
    return ok(sitemapService::getSitemapXml, XML);
  }

  @GET
  @Path("/content-sitemap.xml")
  @Produces(XML)
  public Response contentSitemap() {
    return ok(sitemapService::getContentSitemapXml, XML);
  }

  @GET
  // An ERC may contain slashes, which the content URLs keep unencoded
  @Path("/content/{lng}/{erc: .+}")
  @Produces(HTML)
  public Response content(
      @PathParam("lng") String languageCode, @PathParam("erc") String externalReferenceCode) {
    try {
      return sitemapService
          .getContentPage(languageCode, externalReferenceCode)
          .map(html -> cached(Response.ok(html, HTML)))
          .orElseGet(
              () ->
                  Response.status(Response.Status.NOT_FOUND)
                      .type(TEXT)
                      .entity("Not found")
                      .build());
    } catch (IllegalStateException e) {
      return unavailable(e);
    }
  }

  private static Response ok(Supplier<byte[]> body, String type) {
    try {
      return cached(Response.ok(body.get(), type));
    } catch (IllegalStateException e) {
      return unavailable(e);
    }
  }

  private static Response cached(Response.ResponseBuilder responseBuilder) {
    return responseBuilder.header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL).build();
  }

  private static Response unavailable(IllegalStateException e) {
    log.error("Sitemap is not available", e);
    return Response.status(Response.Status.SERVICE_UNAVAILABLE)
        .type(TEXT)
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .entity("Service unavailable")
        .build();
  }
}
