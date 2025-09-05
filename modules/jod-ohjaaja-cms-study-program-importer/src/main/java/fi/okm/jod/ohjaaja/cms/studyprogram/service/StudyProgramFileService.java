/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.studyprogram.service;

import static fi.okm.jod.ohjaaja.cms.studyprogram.constants.StudyProgramImporterConstants.IMAGE_FOLDER_NAME;
import static fi.okm.jod.ohjaaja.cms.studyprogram.constants.StudyProgramImporterConstants.JOD_GROUP_ID;
import static fi.okm.jod.ohjaaja.cms.studyprogram.util.StudyProgramImporterUtil.getUser;

import com.liferay.document.library.kernel.model.DLVersionNumberIncrease;
import com.liferay.document.library.kernel.service.DLAppLocalService;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.repository.model.Folder;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.util.HttpUtil;
import com.liferay.portal.kernel.util.MimeTypesUtil;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.StringUtil;
import fi.okm.jod.ohjaaja.cms.studyprogram.constants.StudyProgramImporterConstants;
import fi.okm.jod.ohjaaja.cms.studyprogram.service.exception.StudyProgramImageFolderDeleteException;
import java.net.URI;
import java.nio.file.Paths;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = StudyProgramFileService.class)
public class StudyProgramFileService {
  private static final Log log = LogFactoryUtil.getLog(StudyProgramFileService.class);
  @Reference private DLAppLocalService dlAppLocalService;

  public String getImageJSON(String imageUrl, String oid) {
    try {

      String fileName;
      try {
        var uri = new URI(imageUrl);
        fileName = Paths.get(uri.getPath()).getFileName().toString();
      } catch (Exception e) {
        fileName = "studyprogram-" + oid + ".jpg";
      }
      var bytes = HttpUtil.URLtoByteArray(imageUrl);

      var userId = getUser(PortalUtil.getDefaultCompanyId()).getUserId();
      var folderId = getOrCreateImageFolderId(userId);

      var serviceContext = new ServiceContext();
      serviceContext.setScopeGroupId(JOD_GROUP_ID);
      serviceContext.setUserId(userId);
      serviceContext.setAddGuestPermissions(true);
      serviceContext.setAddGuestPermissions(true);

      FileEntry fileEntry = null;
      try {
        fileEntry = dlAppLocalService.getFileEntryByExternalReferenceCode(oid, JOD_GROUP_ID);
      } catch (PortalException e) {
        log.error(
            "No existing file entry found for external reference code: "
                + oid
                + ". Creating a new one.");
      }

      if (fileEntry != null) {
        // If file entry already exists, update it
        fileEntry =
            dlAppLocalService.updateFileEntry(
                fileEntry.getUserId(),
                fileEntry.getFileEntryId(),
                fileName,
                MimeTypesUtil.getContentType(fileName),
                fileName,
                fileName,
                null,
                null,
                DLVersionNumberIncrease.AUTOMATIC,
                bytes,
                null,
                null,
                null,
                serviceContext);

      } else {
        fileEntry =
            dlAppLocalService.addFileEntry(
                oid, // externalReferenceCode
                userId,
                JOD_GROUP_ID,
                folderId,
                fileName,
                MimeTypesUtil.getContentType(fileName),
                fileName,
                fileName,
                null,
                null,
                bytes,
                null,
                null,
                null,
                serviceContext);
      }
      return String.format(
          """
            {
              "alt": "%s",
              "groupId": %d,
              "name": "%s",
              "title": "%s",
              "type": "document",
              "uuid": "%s"
            }
          """,
          fileEntry.getTitle(),
          fileEntry.getGroupId(),
          fileEntry.getFileName(),
          fileEntry.getTitle(),
          fileEntry.getUuid());

    } catch (Exception e) {
      log.error("Failed to get image JSON for URL: " + imageUrl, e);
    }
    return null;
  }

  public void deleteStudyProgramImageFolder() throws StudyProgramImageFolderDeleteException {
    Folder folder;
    try {
      folder = dlAppLocalService.getFolder(JOD_GROUP_ID, 0, IMAGE_FOLDER_NAME);
    } catch (PortalException e) {
      log.error("DL folder not found: " + IMAGE_FOLDER_NAME);
      return;
    }
    try {
      if (folder != null) {
        dlAppLocalService.deleteFolder(folder.getFolderId());
        log.info("Deleted DL folder: " + IMAGE_FOLDER_NAME);
      }
    } catch (PortalException e) {
      log.error("Failed to delete dl folder: " + IMAGE_FOLDER_NAME, e);
      throw new StudyProgramImageFolderDeleteException(
          "Failed to delete dl folder: " + IMAGE_FOLDER_NAME);
    }
  }

  private long getOrCreateImageFolderId(long userId) throws PortalException {
    try {
      var folder = dlAppLocalService.getFolder(JOD_GROUP_ID, 0, IMAGE_FOLDER_NAME);
      return folder.getFolderId();
    } catch (PortalException e) {
      var ctx = new ServiceContext();
      ctx.setScopeGroupId(JOD_GROUP_ID);
      ctx.setAddGuestPermissions(true);
      ctx.setAddGroupPermissions(true);
      var folder =
          dlAppLocalService.addFolder(
              StringUtil.randomString(),
              userId,
              JOD_GROUP_ID,
              0,
              IMAGE_FOLDER_NAME,
              StudyProgramImporterConstants.IMAGE_FOLDER_DESCRIPTION,
              ctx);
      return folder.getFolderId();
    }
  }
}
