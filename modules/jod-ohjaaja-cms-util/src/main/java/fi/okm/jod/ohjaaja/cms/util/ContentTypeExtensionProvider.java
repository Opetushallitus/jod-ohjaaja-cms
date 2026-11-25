/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.util;

import com.liferay.dynamic.data.mapping.service.DDMStructureLocalService;
import com.liferay.headless.delivery.dto.v1_0.StructuredContent;
import com.liferay.portal.vulcan.extension.ExtensionProvider;
import com.liferay.portal.vulcan.extension.PropertyDefinition;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Adds the read-only {@value #CONTENT_TYPE} field ({@link ContentType}) to the structured contents
 * of headless delivery, so that the clients do not need to know the environment specific structure
 * ids.
 */
@Component(service = ExtensionProvider.class)
public class ContentTypeExtensionProvider implements ExtensionProvider {

  public static final String CONTENT_TYPE = "contentType";

  @Reference private DDMStructureLocalService ddmStructureLocalService;

  @Override
  public boolean isApplicableExtension(long companyId, String className) {
    return StructuredContent.class.getName().equals(className);
  }

  @Override
  public Map<String, Serializable> getExtendedProperties(
      long companyId, long userId, String className, Object entity) {
    if (!(entity instanceof StructuredContent structuredContent)
        || structuredContent.getContentStructureId() == null) {
      return Map.of();
    }
    var structure =
        ddmStructureLocalService.fetchDDMStructure(structuredContent.getContentStructureId());
    if (structure == null) {
      return Map.of();
    }
    return Map.of(
        CONTENT_TYPE,
        ContentType.fromStructureExternalReferenceCode(structure.getExternalReferenceCode())
            .name());
  }

  @Override
  public Map<String, PropertyDefinition> getExtendedPropertyDefinitions(
      long companyId, String className) {
    // Not required: the clients that write structured contents do not send it
    return Map.of(
        CONTENT_TYPE,
        new PropertyDefinition(
            "The type of the content, ARTICLE or NEWS. Read-only.",
            CONTENT_TYPE,
            PropertyDefinition.PropertyType.TEXT,
            false));
  }

  @Override
  public Collection<String> getFilteredPropertyNames(long companyId, Object entity) {
    return List.of();
  }

  @Override
  public void setExtendedProperties(
      long companyId,
      long userId,
      String className,
      Object entity,
      Map<String, Serializable> extendedProperties) {
    // Read-only, derived from the structure
  }
}
