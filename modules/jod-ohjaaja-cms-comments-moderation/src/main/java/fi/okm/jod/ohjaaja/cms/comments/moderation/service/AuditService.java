/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.comments.moderation.service;

import com.liferay.portal.kernel.audit.AuditMessage;
import com.liferay.portal.kernel.audit.AuditRouter;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.WebKeys;
import javax.portlet.PortletRequest;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = AuditService.class)
public class AuditService {
  private static final Log log = LogFactoryUtil.getLog(AuditService.class);
  @Reference private AuditRouter auditRouter;
  @Reference private UserLocalService userLocalService;

  public void audit(String action, PortletRequest portletRequest) {
    ThemeDisplay themeDisplay = (ThemeDisplay) portletRequest.getAttribute(WebKeys.THEME_DISPLAY);
    var userId = themeDisplay.getUserId();
    try {
      User user = userLocalService.getUser(userId);
      AuditMessage auditMessage =
          new AuditMessage(action, user.getCompanyId(), user.getUserId(), user.getFullName());
      auditRouter.route(auditMessage);
    } catch (Exception e) {
      log.error("Audit failed for action " + action + " by user " + userId, e);
    }
  }
}
