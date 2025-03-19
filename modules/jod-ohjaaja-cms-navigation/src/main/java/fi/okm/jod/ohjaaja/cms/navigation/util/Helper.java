/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.navigation.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.StringWriter;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;

public class Helper {

  private static final ObjectMapper mapper = new ObjectMapper();

  public static Response toResponse(Object pojo) {

    ObjectNode objectNode = mapper.createObjectNode();
    objectNode.putPOJO("user", pojo);

    StringWriter writer = new StringWriter();
    try {
      mapper.writeValue(writer, pojo);
      return Response.ok().entity(writer.toString()).build();
    } catch (IOException e) {
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  public static String getPreferredLanguage(HttpServletRequest request) {
    return request.getLocale().toLanguageTag();
  }
}
