/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.util;

import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.util.PortalUtil;
import java.util.Map;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(immediate = true, service = JodOhjaajaCmsUtil.class)
public class JodOhjaajaCmsUtilImpl implements JodOhjaajaCmsUtil {

  private Group jodOhjaajaCmsGroup;

  @Reference GroupLocalService groupLocalService;

  @Activate
  protected void activate(Map<String, Object> properties) {
    System.out.println("JodOhjaajaCmsUtilImpl activated");
  }

  @Override
  public Group getJodOhjaajaCmsGroup() {
    if (this.jodOhjaajaCmsGroup == null) {
      groupLocalService.getActiveGroups(PortalUtil.getDefaultCompanyId(), true).stream()
          .filter(group -> group.getFriendlyURL().equals("/guest"))
          .findFirst()
          .ifPresentOrElse(
              group -> this.jodOhjaajaCmsGroup = group,
              () -> {
                throw new RuntimeException("Jod Ohjaaja CMS group not found");
              });
    }
    return this.jodOhjaajaCmsGroup;
  }
}
