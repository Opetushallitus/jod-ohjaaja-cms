/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.util;

import com.liferay.asset.kernel.model.AssetCategoryConstants;
import com.liferay.asset.kernel.model.AssetVocabulary;
import com.liferay.asset.kernel.model.AssetVocabularyConstants;
import com.liferay.asset.kernel.service.AssetVocabularyLocalService;
import com.liferay.journal.model.JournalArticle;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.ResourceConstants;
import com.liferay.portal.kernel.model.role.RoleConstants;
import com.liferay.portal.kernel.security.permission.ActionKeys;
import com.liferay.portal.kernel.security.permission.ResourceActionsUtil;
import com.liferay.portal.kernel.service.ResourcePermissionLocalService;
import com.liferay.portal.kernel.service.RoleLocalService;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.vulcan.util.LocalizedMapUtil;
import com.liferay.portlet.asset.util.AssetVocabularySettingsHelper;
import java.util.LinkedHashSet;
import java.util.Map;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(immediate = true, service = VocabularyInitializer.class)
public class VocabularyInitializerImpl implements VocabularyInitializer {

  private static final Log log = LogFactoryUtil.getLog(VocabularyInitializerImpl.class);

  @Reference private AssetVocabularyLocalService assetVocabularyLocalService;
  @Reference private ResourcePermissionLocalService resourcePermissionLocalService;
  @Reference private RoleLocalService roleLocalService;
  @Reference private JodOhjaajaCmsUtil jodOhjaajaCmsUtil;
  @Reference private Portal portal;

  @Override
  public synchronized AssetVocabulary getOrCreateVocabulary(
      String externalReferenceCode,
      String legacyExternalReferenceCode,
      String name,
      Map<String, String> titles)
      throws PortalException {
    var vocabulary = fetchVocabulary(externalReferenceCode);
    if (vocabulary == null) {
      vocabulary =
          takeOverVocabulary(externalReferenceCode, legacyExternalReferenceCode, name, titles);
    }
    if (vocabulary == null) {
      vocabulary = addVocabulary(externalReferenceCode, name, titles);
    }
    ensureGuestViewPermission(
        vocabulary.getCompanyId(), AssetVocabulary.class.getName(), vocabulary.getVocabularyId());
    return vocabulary;
  }

  @Override
  public AssetVocabulary fetchVocabulary(String externalReferenceCode) {
    return assetVocabularyLocalService.fetchAssetVocabularyByExternalReferenceCode(
        externalReferenceCode, getGroupId());
  }

  private long getGroupId() {
    return jodOhjaajaCmsUtil.getJodOhjaajaCmsGroup().getGroupId();
  }

  private AssetVocabulary takeOverVocabulary(
      String externalReferenceCode,
      String legacyExternalReferenceCode,
      String name,
      Map<String, String> titles)
      throws PortalException {
    AssetVocabulary vocabulary = null;
    if (legacyExternalReferenceCode != null) {
      vocabulary = fetchVocabulary(legacyExternalReferenceCode);
    }
    if (vocabulary == null) {
      vocabulary =
          assetVocabularyLocalService.getGroupVocabularies(getGroupId()).stream()
              // Not one managed as another vocabulary
              .filter(
                  groupVocabulary ->
                      !TakeOverUtil.isManaged(groupVocabulary.getExternalReferenceCode())
                          && (name.equals(groupVocabulary.getName())
                              || TakeOverUtil.hasAnyTitle(
                                  groupVocabulary.getTitleMap(), titles.values())))
              .findFirst()
              .orElse(null);
    }
    if (vocabulary == null) {
      return null;
    }

    var previousExternalReferenceCode = vocabulary.getExternalReferenceCode();
    vocabulary.setExternalReferenceCode(externalReferenceCode);
    vocabulary = assetVocabularyLocalService.updateAssetVocabulary(vocabulary);
    log.info(
        "Took over vocabulary "
            + vocabulary.getVocabularyId()
            + ", changed its external reference code from "
            + previousExternalReferenceCode
            + " to "
            + externalReferenceCode);
    return vocabulary;
  }

  private AssetVocabulary addVocabulary(
      String externalReferenceCode, String name, Map<String, String> titles)
      throws PortalException {
    var group = jodOhjaajaCmsUtil.getJodOhjaajaCmsGroup();
    var assetVocabularySettingsHelper = new AssetVocabularySettingsHelper();
    assetVocabularySettingsHelper.setMultiValued(true);
    assetVocabularySettingsHelper.setClassNameIdsAndClassTypePKs(
        new long[] {portal.getClassNameId(JournalArticle.class.getName())},
        new long[] {AssetCategoryConstants.ALL_CLASS_TYPE_PK},
        new boolean[] {false});

    var serviceContext = new ServiceContext();
    serviceContext.setAddGroupPermissions(true);
    serviceContext.setAddGuestPermissions(true);
    var vocabulary =
        assetVocabularyLocalService.addVocabulary(
            externalReferenceCode,
            AdminUtil.getAdminUser(group.getCompanyId()).getUserId(),
            group.getGroupId(),
            name,
            null,
            LocalizedMapUtil.getLocalizedMap(titles),
            null,
            assetVocabularySettingsHelper.toString(),
            AssetVocabularyConstants.VISIBILITY_TYPE_PUBLIC,
            serviceContext);
    log.info("Created vocabulary " + externalReferenceCode);
    return vocabulary;
  }

  @Override
  public void ensureGuestViewPermission(long companyId, String className, long primKey)
      throws PortalException {
    var guestRole = roleLocalService.getRole(companyId, RoleConstants.GUEST);
    var primKeyString = String.valueOf(primKey);
    // hasResourcePermission would throw if the guest has no permissions on the model
    var resourcePermission =
        resourcePermissionLocalService.fetchResourcePermission(
            companyId,
            className,
            ResourceConstants.SCOPE_INDIVIDUAL,
            primKeyString,
            guestRole.getRoleId());
    if (resourcePermission != null && resourcePermission.hasActionId(ActionKeys.VIEW)) {
      return;
    }
    // The other actions of the guest are kept. addResourcePermission cannot be used, as it fails if
    // the guest has a permission without any actions, e.g. after they were removed in the UI.
    var actionIds = new LinkedHashSet<String>();
    if (resourcePermission != null) {
      for (var actionId : ResourceActionsUtil.getModelResourceActions(className)) {
        if (resourcePermission.hasActionId(actionId)) {
          actionIds.add(actionId);
        }
      }
    }
    actionIds.add(ActionKeys.VIEW);
    resourcePermissionLocalService.setResourcePermissions(
        companyId,
        className,
        ResourceConstants.SCOPE_INDIVIDUAL,
        primKeyString,
        guestRole.getRoleId(),
        actionIds.toArray(new String[0]));
    log.info("Granted the guest the view permission on " + className + " " + primKey);
  }
}
