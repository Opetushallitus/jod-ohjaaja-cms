/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.auth.role;

import com.liferay.portal.kernel.model.role.RoleConstants;
import java.util.Collection;
import java.util.List;

/** Fixed mapping from Cognito groups to Liferay roles. */
public final class RoleMapping {

  /**
   * A Liferay role that is granted to members of a Cognito group.
   *
   * @param cognitoGroup Cognito group name
   * @param roleName Liferay role name (key)
   */
  public record MappedRole(String cognitoGroup, String roleName) {}

  public static final List<MappedRole> MAPPED_ROLES =
      List.of(
          new MappedRole("cms-admin", RoleConstants.ADMINISTRATOR),
          new MappedRole("cms-sisallontuottaja", ContentProducerRole.NAME));

  private RoleMapping() {}

  /** Returns the mapped roles the given Cognito groups entitle to. */
  public static List<MappedRole> resolve(Collection<String> cognitoGroups) {
    return MAPPED_ROLES.stream()
        .filter(mappedRole -> cognitoGroups.contains(mappedRole.cognitoGroup()))
        .toList();
  }
}
