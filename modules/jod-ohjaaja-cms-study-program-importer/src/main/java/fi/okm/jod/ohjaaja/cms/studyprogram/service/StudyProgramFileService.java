/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.studyprogram.service;

import static fi.okm.jod.ohjaaja.cms.studyprogram.constants.StudyProgramImporterConstants.*;

import com.liferay.document.library.kernel.model.DLVersionNumberIncrease;
import com.liferay.document.library.kernel.service.DLAppLocalService;
import com.liferay.document.library.kernel.service.DLFileEntryLocalService;
import com.liferay.document.library.kernel.service.DLTrashLocalService;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.repository.model.Folder;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.util.MimeTypesUtil;
import com.liferay.portal.kernel.util.StringUtil;
import fi.okm.jod.ohjaaja.cms.studyprogram.client.StudyProgramImageClient;
import fi.okm.jod.ohjaaja.cms.studyprogram.constants.StudyProgramImporterConstants;
import fi.okm.jod.ohjaaja.cms.studyprogram.service.exception.StudyProgramImageFolderDeleteException;
import fi.okm.jod.ohjaaja.cms.util.JodOhjaajaCmsUtil;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = StudyProgramFileService.class)
public class StudyProgramFileService {
  private static final Log log = LogFactoryUtil.getLog(StudyProgramFileService.class);
  @Reference private DLAppLocalService dlAppLocalService;
  @Reference private DLFileEntryLocalService dlFileEntryLocalService;
  @Reference private DLTrashLocalService dlTrashLocalService;
  @Reference private JodOhjaajaCmsUtil jodOhjaajaCmsUtil;
  @Reference private StudyProgramImageClient studyProgramImageClient;

  /**
   * Downloads the image of a study program and stores it under the oid of the study program.
   *
   * @return the image, or null if it could not be imported
   * @throws InterruptedException if the download is interrupted, so that the import stops instead
   *     of updating the article without its image
   */
  public FileEntry getStudyProgramImage(String imageUrl, String oid, long userId)
      throws InterruptedException {
    try {
      var image = studyProgramImageClient.downloadImage(imageUrl);
      return getOrCreateFileEntryByExternalReferenceCode(
          oid, getImageFileName(oid, image.extension()), image.bytes(), userId);
    } catch (IOException | RuntimeException e) {
      log.error("Failed to fetch or store image from URL: " + imageUrl, e);
      return null;
    }
  }

  private static String getImageFileName(String oid, String extension) {
    return "studyprogram-" + oid + "." + extension;
  }

  /**
   * @return the image with the given UUID, or null if it has been deleted or moved to the Recycle
   *     Bin
   */
  public FileEntry findImage(String uuid, long groupId) {
    try {
      var fileEntry = dlAppLocalService.getFileEntryByUuidAndGroupId(uuid, groupId);
      return isInTrash(fileEntry) ? null : fileEntry;
    } catch (PortalException e) {
      log.info("Image " + uuid + " not found: " + e.getMessage());
      return null;
    }
  }

  /**
   * @return the placeholder image, or if it could not be imported, the one from an earlier import,
   *     or null if there is none
   */
  public FileEntry getStudyProgramImagePlaceholder(long userId) {

    try {
      var bytes =
          readImageBytes(StudyProgramImporterConstants.STUDY_PROGRAM_PLACEHOLDER_IMAGE_PATH);
      var placeholder =
          getOrCreateFileEntryByExternalReferenceCode(
              STUDY_PROGRAM_PLACEHOLDER_IMAGE_ERC,
              STUDY_PROGRAM_PLACEHOLDER_IMAGE_FILE_NAME,
              bytes,
              userId);
      if (placeholder != null) {
        return placeholder;
      }
    } catch (IOException e) {
      log.error("Failed to read placeholder image from resources", e);
    }
    // The placeholder from an earlier import is shown until the placeholder can be imported again
    var earlierPlaceholder = findExistingFileEntry(STUDY_PROGRAM_PLACEHOLDER_IMAGE_ERC);
    return earlierPlaceholder == null || isInTrash(earlierPlaceholder) ? null : earlierPlaceholder;
  }

  public FileEntry getOrCreateFileEntryByExternalReferenceCode(
      String externalReferenceCode, String fileName, byte[] bytes, long userId) {
    try {
      var folderId = getOrCreateImageFolderId(userId);

      var serviceContext = new ServiceContext();
      serviceContext.setScopeGroupId(jodOhjaajaCmsUtil.getJodOhjaajaCmsGroup().getGroupId());
      serviceContext.setUserId(userId);
      serviceContext.setAddGroupPermissions(true);
      serviceContext.setAddGuestPermissions(true);

      FileEntry fileEntry = findExistingFileEntry(externalReferenceCode);
      if (fileEntry != null && isInTrash(fileEntry)) {
        // The file keeps its external reference code in the Recycle Bin, so it is restored instead
        // of adding a new file. Unlike deleting it, this does not lose the file if storing the new
        // content fails.
        fileEntry =
            dlTrashLocalService.moveFileEntryFromTrash(
                userId,
                fileEntry.getRepositoryId(),
                fileEntry.getFileEntryId(),
                folderId,
                serviceContext);
        log.info("Restored image " + fileEntry.getFileName() + " from the Recycle Bin");
      }

      if (fileEntry != null && isSameFile(fileEntry, fileName, bytes)) {
        // Updating an unchanged file would only add a new version of it
        return fileEntry;
      } else if (fileEntry != null) {
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
                externalReferenceCode,
                userId,
                jodOhjaajaCmsUtil.getJodOhjaajaCmsGroup().getGroupId(),
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
      return fileEntry;

    } catch (Exception e) {
      log.error(
          "Failed to get or create image " + fileName + " (ERC: " + externalReferenceCode + ")", e);
    }
    return null;
  }

  /**
   * @return false also if the content of the file entry cannot be read, so that it is overwritten
   */
  private boolean isSameFile(FileEntry fileEntry, String fileName, byte[] bytes) {
    if (!fileName.equals(fileEntry.getFileName()) || fileEntry.getSize() != bytes.length) {
      return false;
    }
    // FileEntry.getContentStream() would increment the view count of the file
    try (var inputStream =
        dlFileEntryLocalService.getFileAsStream(
            fileEntry.getFileEntryId(), fileEntry.getVersion(), false)) {
      return Arrays.equals(inputStream.readAllBytes(), bytes);
    } catch (PortalException | IOException e) {
      log.warn("Failed to read image " + fileName + ". Overwriting it.", e);
      return false;
    }
  }

  private static boolean isInTrash(FileEntry fileEntry) {
    return fileEntry.isInTrash() || fileEntry.isInTrashContainer();
  }

  private FileEntry findExistingFileEntry(String externalReferenceCode) {
    try {
      return dlAppLocalService.getFileEntryByExternalReferenceCode(
          externalReferenceCode, jodOhjaajaCmsUtil.getJodOhjaajaCmsGroup().getGroupId());
    } catch (PortalException e) {
      log.info(
          "No existing file entry found for external reference code: " + externalReferenceCode);
      return null;
    }
  }

  public byte[] readImageBytes(String resourcePath) throws IOException {
    try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
      if (is == null) {
        throw new IOException("Resource not found: " + resourcePath);
      }
      return is.readAllBytes();
    }
  }

  public void deleteStudyProgramImageFolder() throws StudyProgramImageFolderDeleteException {
    Folder folder;
    try {
      folder =
          dlAppLocalService.getFolder(
              jodOhjaajaCmsUtil.getJodOhjaajaCmsGroup().getGroupId(), 0, IMAGE_FOLDER_NAME);
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
      var folder =
          dlAppLocalService.getFolder(
              jodOhjaajaCmsUtil.getJodOhjaajaCmsGroup().getGroupId(), 0, IMAGE_FOLDER_NAME);
      return folder.getFolderId();
    } catch (PortalException e) {
      var ctx = new ServiceContext();
      ctx.setScopeGroupId(jodOhjaajaCmsUtil.getJodOhjaajaCmsGroup().getGroupId());
      ctx.setAddGuestPermissions(true);
      ctx.setAddGroupPermissions(true);
      var folder =
          dlAppLocalService.addFolder(
              StringUtil.randomString(),
              userId,
              jodOhjaajaCmsUtil.getJodOhjaajaCmsGroup().getGroupId(),
              0,
              IMAGE_FOLDER_NAME,
              StudyProgramImporterConstants.IMAGE_FOLDER_DESCRIPTION,
              ctx);
      return folder.getFolderId();
    }
  }
}
