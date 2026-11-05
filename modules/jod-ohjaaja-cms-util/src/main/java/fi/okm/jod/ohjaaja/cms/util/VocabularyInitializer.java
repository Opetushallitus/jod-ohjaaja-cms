/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.util;

import com.liferay.asset.kernel.model.AssetVocabulary;
import com.liferay.portal.kernel.exception.PortalException;
import java.util.Map;

/**
 * Creates or takes over the vocabularies of the JOD Ohjaaja site in one way: a vocabulary created
 * by hand is only given its external reference code and can otherwise be edited in the UI, and the
 * guest can always view it, as the public UI shows its categories.
 */
public interface VocabularyInitializer {

  /**
   * Returns the vocabulary of the JOD Ohjaaja site with the external reference code. A vocabulary
   * of the site without it is taken over by the legacy external reference code, or by the name or
   * any of the titles. A missing one is created. Other sites, such as the global one, are not
   * searched, so that an unrelated vocabulary with the same name is not taken over.
   *
   * @param legacyExternalReferenceCode the former external reference code, or null
   * @param titles the titles by language ID, e.g. fi_FI
   */
  AssetVocabulary getOrCreateVocabulary(
      String externalReferenceCode,
      String legacyExternalReferenceCode,
      String name,
      Map<String, String> titles)
      throws PortalException;

  /** The vocabulary of the JOD Ohjaaja site with the external reference code, or null. */
  AssetVocabulary fetchVocabulary(String externalReferenceCode);

  /**
   * Grants the guest the view permission on the model if it does not have it, keeping its other
   * permissions.
   */
  void ensureGuestViewPermission(long companyId, String className, long primKey)
      throws PortalException;
}
