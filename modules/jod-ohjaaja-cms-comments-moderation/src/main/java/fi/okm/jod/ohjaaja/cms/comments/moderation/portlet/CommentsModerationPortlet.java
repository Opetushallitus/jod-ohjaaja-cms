/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.comments.moderation.portlet;

import com.liferay.portal.kernel.portlet.bridges.mvc.MVCPortlet;
import com.liferay.portal.kernel.servlet.SessionErrors;
import com.liferay.portal.kernel.servlet.SessionMessages;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.PropsUtil;
import fi.okm.jod.ohjaaja.cms.comments.moderation.client.Feature;
import fi.okm.jod.ohjaaja.cms.comments.moderation.client.exception.ModerationApiException;
import fi.okm.jod.ohjaaja.cms.comments.moderation.constants.CommentsModerationPortletKeys;
import fi.okm.jod.ohjaaja.cms.comments.moderation.dto.CommentDto;
import fi.okm.jod.ohjaaja.cms.comments.moderation.dto.CommentReportSummaryDto;
import fi.okm.jod.ohjaaja.cms.comments.moderation.dto.PageDto;
import fi.okm.jod.ohjaaja.cms.comments.moderation.service.CommentsModerationService;
import fi.okm.jod.ohjaaja.cms.comments.moderation.service.FeatureFlagsService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.portlet.*;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(
    property = {
      "com.liferay.portlet.css-class-wrapper=portlet-comments.moderation",
      "com.liferay.portlet.display-category=category.moderation",
      "com.liferay.portlet.preferences-owned-by-group=true",
      "com.liferay.portlet.private-request-attributes=false",
      "com.liferay.portlet.private-session-attributes=false",
      "com.liferay.portlet.remoteable=false",
      "com.liferay.portlet.render-weight=50",
      "com.liferay.portlet.use-default-template=true",
      "javax.portlet.display-name=Moderointi",
      "javax.portlet.expiration-cache=0",
      "javax.portlet.init-param.always-display-default-configuration-icons=true",
      "javax.portlet.init-param.view-template=/view.jsp",
      "javax.portlet.name=" + CommentsModerationPortletKeys.COMMENTS_MODERATION,
      "javax.portlet.resource-bundle=content.Language",
      "javax.portlet.security-role-ref=administrator,power-user",
      "javax.portlet.supports.mime-type=text/html",
      "javax.portlet.supported-locale=en_US,fi_FI"
    },
    service = Portlet.class)
public class CommentsModerationPortlet extends MVCPortlet {

  private static final String OHJAAJA_FRONTEND_URL = PropsUtil.get("ohjaaja.frontend.url");

  @Reference private CommentsModerationService commentsModerationService;
  @Reference private FeatureFlagsService featureFlagsService;

  @Override
  public void doView(RenderRequest renderRequest, RenderResponse renderResponse)
      throws PortletException, IOException {

    String tab = ParamUtil.getString(renderRequest, "tab", "reported");
    renderRequest.setAttribute(
        "ohjaajaArticleShortUrlPrefix", OHJAAJA_FRONTEND_URL + "/fi/artikkeli/");
    renderRequest.setAttribute(
        "commentsEnabled", featureFlagsService.isFeatureEnabled(renderRequest, Feature.COMMENTS));

    if ("reported".equals(tab)) {
      try {
        renderRequest.setAttribute(
            "commentReportSummaries",
            commentsModerationService.getCommentReportSummaries(renderRequest));

      } catch (ModerationApiException e) {
        SessionErrors.add(renderRequest, "error.fetching.comment.reports");
        renderRequest.setAttribute(
            "commentReportSummaries", new ArrayList<CommentReportSummaryDto>());
      }
    } else {
      int currentPage = ParamUtil.getInteger(renderRequest, "currentPage", 1);
      int pageSize = ParamUtil.getInteger(renderRequest, "pageSize", 10);

      try {
        PageDto<CommentDto> commentsPage =
            commentsModerationService.getComments(renderRequest, currentPage - 1, pageSize);
        renderRequest.setAttribute("commentsPage", commentsPage);
      } catch (ModerationApiException e) {
        SessionErrors.add(renderRequest, "error.fetching.comments");
        renderRequest.setAttribute("commentsPage", new PageDto<>(List.of(), 0, 0));
      }
    }

    super.doView(renderRequest, renderResponse);
  }

  public void deleteCommentAction(ActionRequest actionRequest, ActionResponse actionResponse) {
    var commentId = ParamUtil.getString(actionRequest, "commentId");
    try {
      commentsModerationService.deleteComment(UUID.fromString(commentId), actionRequest);
      SessionMessages.add(actionRequest, "success.comment.deleted");
    } catch (ModerationApiException e) {
      SessionErrors.add(actionRequest, "error.deleting.comment");
    }
  }

  public void deleteCommentReportsAction(
      ActionRequest actionRequest, ActionResponse actionResponse) {
    var commentId = ParamUtil.getString(actionRequest, "commentId");
    try {
      commentsModerationService.deleteCommentReports(UUID.fromString(commentId), actionRequest);
      SessionMessages.add(actionRequest, "success.reports.deleted");
    } catch (ModerationApiException e) {
      SessionErrors.add(actionRequest, "error.deleting.comment.reports");
    }
  }

  public void setCommentsFeatureFlag(ActionRequest actionRequest, ActionResponse actionResponse) {
    boolean enabled = ParamUtil.getBoolean(actionRequest, "enabled");
    featureFlagsService.updateFeatureFlag(actionRequest, Feature.COMMENTS, enabled);
    SessionMessages.add(
        actionRequest, enabled ? "success.comments.enabled" : "success.comments.disabled");
  }
}
