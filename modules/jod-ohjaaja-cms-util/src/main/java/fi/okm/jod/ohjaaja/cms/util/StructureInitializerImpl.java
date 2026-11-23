/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.util;

import com.liferay.data.engine.rest.dto.v2_0.DataDefinition;
import com.liferay.data.engine.rest.resource.v2_0.DataDefinitionResource;
import com.liferay.dynamic.data.mapping.constants.DDMStructureConstants;
import com.liferay.dynamic.data.mapping.exception.NoSuchStructureException;
import com.liferay.dynamic.data.mapping.model.DDMStructure;
import com.liferay.dynamic.data.mapping.service.DDMStructureLocalService;
import com.liferay.journal.constants.JournalFolderConstants;
import com.liferay.journal.model.JournalArticle;
import com.liferay.journal.model.JournalFolder;
import com.liferay.journal.service.JournalFolderLocalService;
import com.liferay.petra.string.StringUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.module.framework.ModuleServiceLifecycle;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.util.Portal;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(immediate = true, service = StructureInitializer.class)
public class StructureInitializerImpl implements StructureInitializer {

  private static final Log log = LogFactoryUtil.getLog(StructureInitializerImpl.class);

  private static final String CONTENT_TYPE = "journal";

  private static final String KEY_CUSTOM_PROPERTIES = "customProperties";
  private static final String KEY_EXTERNAL_REFERENCE_CODE = "externalReferenceCode";
  private static final String KEY_FIELDS = "dataDefinitionFields";
  private static final String KEY_FIELD_TYPE = "fieldType";
  private static final String KEY_NAME = "name";
  private static final String KEY_NESTED_FIELDS = "nestedDataDefinitionFields";
  private static final String FIELD_TYPE_FIELDSET = "fieldset";

  private static final String NEWS_FOLDER_NAME = "Ajankohtaista";
  private static final String NEWS_FOLDER_DESCRIPTION =
      "Ajankohtaista-palstan uutiset. Kansioon voi lisätä vain uutisia.";

  // In dependency order: a fieldset before the structures that use it
  private static final List<Definition> DEFINITIONS =
      List.of(
          new Definition(
              LINK_FIELDSET_EXTERNAL_REFERENCE_CODE,
              "structures/link-fieldset.json",
              // Created by hand before the structures were managed in code, the same in all
              // environments
              "262bb93a-70eb-7f8c-65d7-aa29aa7f2d1d",
              // Not taken over by name: the fieldset of the study program importer has the same
              // name
              false,
              List.of(),
              true),
          new Definition(
              ARTICLE_STRUCTURE_EXTERNAL_REFERENCE_CODE,
              "structures/article.json",
              "2bfe79e6-f496-914a-684e-57719a295a82",
              // e.g. in a local environment created separately from the servers
              true,
              // The name before it was managed in code, the same as on the servers
              List.of("Artikkeli", "Article"),
              false),
          new Definition(
              NEWS_STRUCTURE_EXTERNAL_REFERENCE_CODE,
              "structures/news.json",
              // Managed in code from the start, nothing to take over
              null,
              false,
              List.of(),
              false));

  @Reference private DataDefinitionResource.Factory dataDefinitionResourceFactory;
  @Reference private DDMStructureLocalService ddmStructureLocalService;
  @Reference private JournalFolderLocalService journalFolderLocalService;
  @Reference private JodOhjaajaCmsUtil jodOhjaajaCmsUtil;
  @Reference private Portal portal;

  // The default company and its admin user must exist
  @Reference(target = ModuleServiceLifecycle.PORTAL_INITIALIZED)
  private ModuleServiceLifecycle portalInitialized;

  // A failure is only logged, the structures can also be managed in the UI
  @Activate
  protected void activate() {
    try {
      initStructures();
    } catch (RuntimeException e) {
      log.error("Failed to initialize the structures", e);
    }
  }

  @Override
  public void initStructures() {
    var group = jodOhjaajaCmsUtil.getJodOhjaajaCmsGroup();
    DataDefinitionResource dataDefinitionResource;
    try {
      dataDefinitionResource =
          dataDefinitionResourceFactory
              .create()
              .user(AdminUtil.getAdminUser(group.getCompanyId()))
              .checkPermissions(false)
              .build();
    } catch (PortalException e) {
      log.error("Failed to initialize the structures", e);
      return;
    }

    for (var definition : DEFINITIONS) {
      try {
        initStructure(group, dataDefinitionResource, definition);
      } catch (Exception e) {
        // A failed fieldset makes the structures using it fail too, which is logged for each
        log.error("Failed to initialize structure " + definition.externalReferenceCode(), e);
      }
    }

    try {
      initNewsFolder(group, dataDefinitionResource);
    } catch (Exception e) {
      log.error("Failed to initialize folder " + NEWS_FOLDER_EXTERNAL_REFERENCE_CODE, e);
    }
  }

  private void initNewsFolder(Group group, DataDefinitionResource dataDefinitionResource)
      throws PortalException {
    var groupId = group.getGroupId();
    var newsStructure =
        fetchStructure(dataDefinitionResource, NEWS_STRUCTURE_EXTERNAL_REFERENCE_CODE, groupId);
    if (newsStructure == null) {
      throw new PortalException(
          "Structure " + NEWS_STRUCTURE_EXTERNAL_REFERENCE_CODE + " not found");
    }
    var userId = AdminUtil.getAdminUser(group.getCompanyId()).getUserId();

    var folder =
        journalFolderLocalService.fetchJournalFolderByExternalReferenceCode(
            NEWS_FOLDER_EXTERNAL_REFERENCE_CODE, groupId);
    if (folder != null && folder.isInTrash()) {
      // A content producer can move it to the trash, where it would be deleted with the news
      journalFolderLocalService.restoreFolderFromTrash(userId, folder.getFolderId());
      folder = journalFolderLocalService.getFolder(folder.getFolderId());
      log.warn(
          "Restored folder "
              + NEWS_FOLDER_EXTERNAL_REFERENCE_CODE
              + " ("
              + folder.getFolderId()
              + ") from the trash");
    }
    if (folder == null) {
      var serviceContext = createServiceContext(group, userId);
      // The guests see a news item only if they can see its folder too
      serviceContext.setAddGroupPermissions(true);
      serviceContext.setAddGuestPermissions(true);
      folder =
          journalFolderLocalService.addFolder(
              NEWS_FOLDER_EXTERNAL_REFERENCE_CODE,
              userId,
              groupId,
              JournalFolderConstants.DEFAULT_PARENT_FOLDER_ID,
              NEWS_FOLDER_NAME,
              NEWS_FOLDER_DESCRIPTION,
              serviceContext);
      log.info(
          "Created folder "
              + NEWS_FOLDER_EXTERNAL_REFERENCE_CODE
              + " ("
              + folder.getFolderId()
              + ")");
    }

    if (isNewsFolderUpToDate(folder, newsStructure.getStructureId())) {
      log.info("Folder " + NEWS_FOLDER_EXTERNAL_REFERENCE_CODE + " is up to date");
      return;
    }

    // Left where it is, only its name, description and restriction are managed
    journalFolderLocalService.updateFolder(
        userId,
        groupId,
        folder.getFolderId(),
        folder.getParentFolderId(),
        NEWS_FOLDER_NAME,
        NEWS_FOLDER_DESCRIPTION,
        new long[] {newsStructure.getStructureId()},
        JournalFolderConstants.RESTRICTION_TYPE_DDM_STRUCTURES_AND_WORKFLOW,
        false,
        createServiceContext(group, userId));
    log.info(
        "Updated folder "
            + NEWS_FOLDER_EXTERNAL_REFERENCE_CODE
            + " ("
            + folder.getFolderId()
            + ") to allow only structure "
            + NEWS_STRUCTURE_EXTERNAL_REFERENCE_CODE);
  }

  private boolean isNewsFolderUpToDate(JournalFolder folder, long newsStructureId)
      throws PortalException {
    if (folder.getRestrictionType()
            != JournalFolderConstants.RESTRICTION_TYPE_DDM_STRUCTURES_AND_WORKFLOW
        || !NEWS_FOLDER_NAME.equals(folder.getName())
        || !NEWS_FOLDER_DESCRIPTION.equals(folder.getDescription())) {
      return false;
    }
    var allowedStructureIds =
        journalFolderLocalService
            .getDDMStructures(
                new long[] {folder.getGroupId()},
                folder.getFolderId(),
                JournalFolderConstants.RESTRICTION_TYPE_DDM_STRUCTURES_AND_WORKFLOW)
            .stream()
            .map(DDMStructure::getStructureId)
            .toList();
    return allowedStructureIds.equals(List.of(newsStructureId));
  }

  private static ServiceContext createServiceContext(Group group, long userId) {
    var serviceContext = new ServiceContext();
    serviceContext.setCompanyId(group.getCompanyId());
    serviceContext.setScopeGroupId(group.getGroupId());
    serviceContext.setUserId(userId);
    return serviceContext;
  }

  private void initStructure(
      Group group, DataDefinitionResource dataDefinitionResource, Definition definition)
      throws Exception {
    var groupId = group.getGroupId();
    var desired = readDefinition(definition);
    resolveFieldsetReferences(dataDefinitionResource, desired.getJSONArray(KEY_FIELDS), groupId);

    var structure =
        fetchStructure(dataDefinitionResource, definition.externalReferenceCode(), groupId);
    if (structure == null) {
      structure = takeOverStructure(dataDefinitionResource, definition, desired, groupId);
    }

    if (structure == null) {
      // Not putSiteDataDefinitionByContentTypeByExternalReferenceCode: it creates a missing
      // structure in the global site instead of the given one
      var created =
          dataDefinitionResource.postSiteDataDefinitionByContentType(
              groupId, CONTENT_TYPE, DataDefinition.toDTO(desired.toString()));
      log.info(
          "Created structure " + definition.externalReferenceCode() + " (" + created.getId() + ")");
      setType(definition, ddmStructureLocalService.getStructure(created.getId()));
      return;
    }

    var current =
        JSONFactoryUtil.createJSONObject(
            dataDefinitionResource.getDataDefinition(structure.getStructureId()).toString());
    if (matches(desired, current)) {
      setType(definition, structure);
      log.info("Structure " + definition.externalReferenceCode() + " is up to date");
      return;
    }

    var removedOrChangedFields = getRemovedOrChangedFields(desired, current);
    if (!removedOrChangedFields.isEmpty()) {
      // Not an error: the structure is left as it is on purpose, e.g. one made by hand locally
      log.warn(
          "Not updating structure "
              + definition.externalReferenceCode()
              + ": the definition would remove or change the type of fields "
              + removedOrChangedFields
              + ", which would lose the content of the existing articles");
      return;
    }

    dataDefinitionResource.putDataDefinition(
        structure.getStructureId(), DataDefinition.toDTO(desired.toString()));
    setType(definition, ddmStructureLocalService.getStructure(structure.getStructureId()));
    log.warn(
        "Updated structure "
            + definition.externalReferenceCode()
            + " ("
            + structure.getStructureId()
            + ") to match its definition, overwriting changes made in the UI");
  }

  // The data engine creates every structure as a regular one. A fieldset is a fragment, which is
  // listed only among the fieldsets and not among the structures.
  private void setType(Definition definition, DDMStructure structure) {
    var type =
        definition.fieldset()
            ? DDMStructureConstants.TYPE_FRAGMENT
            : DDMStructureConstants.TYPE_DEFAULT;
    if (structure.getType() != type) {
      structure.setType(type);
      ddmStructureLocalService.updateDDMStructure(structure);
      log.info(
          "Changed the type of structure " + definition.externalReferenceCode() + " to " + type);
    }
  }

  private JSONObject readDefinition(Definition definition) throws IOException, PortalException {
    try (var inputStream = StructureInitializerImpl.class.getResourceAsStream(definition.path())) {
      if (inputStream == null) {
        throw new IOException("Definition " + definition.path() + " not found");
      }
      var json = JSONFactoryUtil.createJSONObject(StringUtil.read(inputStream));
      json.put(KEY_EXTERNAL_REFERENCE_CODE, definition.externalReferenceCode());
      return json;
    }
  }

  // The definitions refer to a fieldset by its external reference code, the IDs differ by
  // environment
  private void resolveFieldsetReferences(
      DataDefinitionResource dataDefinitionResource, JSONArray fields, long groupId)
      throws PortalException {
    if (fields == null) {
      return;
    }
    for (int i = 0; i < fields.length(); i++) {
      var field = fields.getJSONObject(i);
      if (FIELD_TYPE_FIELDSET.equals(field.getString(KEY_FIELD_TYPE))) {
        var customProperties = field.getJSONObject(KEY_CUSTOM_PROPERTIES);
        var fieldsetExternalReferenceCode = customProperties.getString(KEY_EXTERNAL_REFERENCE_CODE);
        var fieldset =
            fetchStructure(dataDefinitionResource, fieldsetExternalReferenceCode, groupId);
        if (fieldset == null) {
          throw new PortalException(
              "Fieldset "
                  + fieldsetExternalReferenceCode
                  + " of field "
                  + field.getString(KEY_NAME)
                  + " not found");
        }
        customProperties.put("ddmStructureId", String.valueOf(fieldset.getStructureId()));
        customProperties.put("ddmStructureKey", fieldset.getStructureKey());
      }
      resolveFieldsetReferences(
          dataDefinitionResource, field.getJSONArray(KEY_NESTED_FIELDS), groupId);
    }
  }

  // Looked up through the data engine, which also creates them
  private DDMStructure fetchStructure(
      DataDefinitionResource dataDefinitionResource, String externalReferenceCode, long groupId)
      throws PortalException {
    DataDefinition dataDefinition;
    try {
      dataDefinition =
          dataDefinitionResource.getSiteDataDefinitionByContentTypeByExternalReferenceCode(
              groupId, CONTENT_TYPE, externalReferenceCode);
    } catch (NoSuchStructureException e) {
      return null;
    } catch (Exception e) {
      throw new PortalException("Failed to get structure " + externalReferenceCode, e);
    }
    return ddmStructureLocalService.getStructure(dataDefinition.getId());
  }

  /**
   * Finds a structure created before it was managed in code: by its former external reference code,
   * or by the name of the definition in any of its languages.
   */
  private DDMStructure takeOverStructure(
      DataDefinitionResource dataDefinitionResource,
      Definition definition,
      JSONObject desired,
      long groupId)
      throws PortalException {
    var structure =
        definition.legacyExternalReferenceCode() == null
            ? null
            : fetchStructure(
                dataDefinitionResource, definition.legacyExternalReferenceCode(), groupId);
    var foundBy = "external reference code " + definition.legacyExternalReferenceCode();
    if (structure == null && definition.takeOverByName()) {
      structure =
          fetchStructureByName(desired.getJSONObject(KEY_NAME), definition.legacyNames(), groupId);
      foundBy = "name";
    }
    if (structure == null) {
      return null;
    }

    var previousExternalReferenceCode = structure.getExternalReferenceCode();
    structure.setExternalReferenceCode(definition.externalReferenceCode());
    structure = ddmStructureLocalService.updateDDMStructure(structure);
    log.info(
        "Took over structure "
            + structure.getStructureId()
            + " found by "
            + foundBy
            + ", changed its external reference code from "
            + previousExternalReferenceCode
            + " to "
            + definition.externalReferenceCode());
    return structure;
  }

  /**
   * A structure with the name of the definition or a former name, in any language, which is not
   * already managed as another structure.
   */
  private DDMStructure fetchStructureByName(
      JSONObject names, List<String> legacyNames, long groupId) throws PortalException {
    var nameValues = new HashSet<String>(legacyNames);
    for (var languageId : names.keySet()) {
      nameValues.add(names.getString(languageId));
    }
    var structures =
        ddmStructureLocalService
            .getStructures(groupId, portal.getClassNameId(JournalArticle.class))
            .stream()
            .filter(
                structure ->
                    !TakeOverUtil.isManaged(structure.getExternalReferenceCode())
                        && TakeOverUtil.hasAnyTitle(structure.getNameMap(), nameValues))
            .toList();
    if (structures.size() > 1) {
      // Neither taken over nor created, so that no duplicate is created either
      throw new PortalException(
          "Several structures are named "
              + nameValues
              + ": "
              + structures.stream().map(DDMStructure::getStructureId).toList());
    }
    return structures.isEmpty() ? null : structures.getFirst();
  }

  /**
   * Whether the current structure has every value of the definition. The values Liferay adds, such
   * as empty translations and defaults, are ignored. Lists of fields and layout rows must match one
   * to one.
   *
   * <p>A definition must leave out the values Liferay manages itself, or the structure is rewritten
   * on every start. E.g. Liferay sets normalizedStructure of a fieldset field to true whenever the
   * structure has no articles, whatever the definition says.
   */
  static boolean matches(Object desired, Object current) {
    if (desired instanceof JSONObject desiredObject) {
      return current instanceof JSONObject currentObject
          && matchesObject(desiredObject, currentObject);
    }
    if (desired instanceof JSONArray desiredArray) {
      return current instanceof JSONArray currentArray
          ? matchesArray(desiredArray, currentArray)
          : isEmpty(desiredArray);
    }
    if (desired == null || current == null) {
      return isEmpty(desired) && isEmpty(current);
    }
    return Objects.equals(String.valueOf(desired), String.valueOf(current));
  }

  // An empty value of the definition is not enforced, e.g. a translation Liferay adds
  private static boolean matchesObject(JSONObject desired, JSONObject current) {
    for (var key : desired.keySet()) {
      var desiredValue = desired.get(key);
      if (!isEmpty(desiredValue)
          && !matches(desiredValue, current.has(key) ? current.get(key) : null)) {
        return false;
      }
    }
    return true;
  }

  private static boolean matchesArray(JSONArray desired, JSONArray current) {
    if (desired.length() != current.length()) {
      return false;
    }
    if (isScalarArray(desired)) {
      // e.g. available languages, in no particular order
      return toStringSet(desired).equals(toStringSet(current));
    }
    for (int i = 0; i < desired.length(); i++) {
      if (!matches(desired.get(i), current.get(i))) {
        return false;
      }
    }
    return true;
  }

  private static boolean isEmpty(Object value) {
    return value == null
        || (value instanceof String string && string.isEmpty())
        || (value instanceof JSONObject object && object.length() == 0)
        || (value instanceof JSONArray array && array.length() == 0);
  }

  private static boolean isScalarArray(JSONArray array) {
    for (int i = 0; i < array.length(); i++) {
      if (array.get(i) instanceof JSONObject || array.get(i) instanceof JSONArray) {
        return false;
      }
    }
    return true;
  }

  private static HashSet<String> toStringSet(JSONArray array) {
    var set = new HashSet<String>();
    for (int i = 0; i < array.length(); i++) {
      set.add(String.valueOf(array.get(i)));
    }
    return set;
  }

  // The content of an article is stored by field name, so a field must keep its name and type
  private static List<String> getRemovedOrChangedFields(JSONObject desired, JSONObject current) {
    var desiredTypes = getFieldTypes(desired.getJSONArray(KEY_FIELDS), new HashMap<>());
    return getFieldTypes(current.getJSONArray(KEY_FIELDS), new HashMap<>()).entrySet().stream()
        .filter(entry -> !entry.getValue().equals(desiredTypes.get(entry.getKey())))
        .map(Map.Entry::getKey)
        .sorted()
        .toList();
  }

  private static Map<String, String> getFieldTypes(JSONArray fields, Map<String, String> types) {
    if (fields == null) {
      return types;
    }
    for (int i = 0; i < fields.length(); i++) {
      var field = fields.getJSONObject(i);
      types.put(field.getString(KEY_NAME), field.getString(KEY_FIELD_TYPE));
      getFieldTypes(field.getJSONArray(KEY_NESTED_FIELDS), types);
    }
    return types;
  }

  private record Definition(
      String externalReferenceCode,
      String path,
      String legacyExternalReferenceCode,
      boolean takeOverByName,
      List<String> legacyNames,
      boolean fieldset) {}
}
