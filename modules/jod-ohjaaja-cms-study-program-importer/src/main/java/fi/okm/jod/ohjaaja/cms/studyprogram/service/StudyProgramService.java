/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.studyprogram.service;

import static fi.okm.jod.ohjaaja.cms.studyprogram.constants.StudyProgramImporterConstants.JOD_GROUP_ID;

import com.liferay.journal.model.JournalArticle;
import com.liferay.journal.service.JournalArticleLocalService;
import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import java.util.*;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author JussiSirén
 */
@Component(service = StudyProgramService.class, immediate = true)
public class StudyProgramService {

  private static final Log log = LogFactoryUtil.getLog(StudyProgramService.class);

  @Reference private JournalArticleLocalService journalArticleLocalService;
  @Reference private StudyProgramStructureService studyProgramStructureService;

  public List<JournalArticle> getImportedStudyPrograms() {

    var structure = studyProgramStructureService.getOrCreateDDMStructure(false);
    if (structure == null) {
      log.warn("No study programs found, DDM structure not available.");
      return Collections.emptyList();
    }

    return journalArticleLocalService.getArticlesByStructureId(
        JOD_GROUP_ID, structure.getStructureId(), QueryUtil.ALL_POS, QueryUtil.ALL_POS, null);
  }
}
