/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.comments.moderation.service;

import static fi.okm.jod.ohjaaja.cms.comments.moderation.util.TokenUtil.getToken;

import fi.okm.jod.ohjaaja.cms.comments.moderation.client.CommentsModerationApiClient;
import fi.okm.jod.ohjaaja.cms.comments.moderation.client.exception.ModerationApiException;
import fi.okm.jod.ohjaaja.cms.comments.moderation.dto.CommentDto;
import fi.okm.jod.ohjaaja.cms.comments.moderation.dto.CommentReportSummaryDto;
import fi.okm.jod.ohjaaja.cms.comments.moderation.dto.PageDto;
import java.util.List;
import java.util.UUID;
import javax.portlet.PortletRequest;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = CommentsModerationService.class)
public class CommentsModerationService {

  @Reference private CommentsModerationApiClient commentsModerationApiClient;
  @Reference private AuditService auditService;

  public PageDto<CommentDto> getComments(
      PortletRequest portletRequest, int pageNumber, int pageSize) throws ModerationApiException {
    return commentsModerationApiClient.fetchComments(
        getToken(portletRequest), pageNumber, pageSize);
  }

  public List<CommentReportSummaryDto> getCommentReportSummaries(PortletRequest portletRequest)
      throws ModerationApiException {
    return commentsModerationApiClient.fetchCommentReportSummaryList(getToken(portletRequest));
  }

  public void deleteCommentReports(UUID commentId, PortletRequest portletRequest)
      throws ModerationApiException {
    commentsModerationApiClient.deleteCommentReports(commentId, getToken(portletRequest));
    auditService.audit("delete-comment-reports", portletRequest);
  }

  public void deleteComment(UUID commentId, PortletRequest portletRequest)
      throws ModerationApiException {
    commentsModerationApiClient.deleteComment(commentId, getToken(portletRequest));
    auditService.audit("delete-comment", portletRequest);
  }
}
