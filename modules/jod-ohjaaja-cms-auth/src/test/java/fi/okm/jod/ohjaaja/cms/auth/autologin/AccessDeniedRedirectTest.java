/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.auth.autologin;

import fi.okm.jod.ohjaaja.cms.auth.user.LoginResult.DenialReason;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Proxy;
import org.junit.Assert;
import org.junit.Test;

/** Unit tests for redirecting verified identities that are not allowed to log in. */
public class AccessDeniedRedirectTest {

  private static final String PATH_MODULE = "/o";

  @Test
  public void testPageLoadIsRedirectedWithReason() {
    Assert.assertEquals(
        "/o/jod-cms-auth/access-denied?reason=no-access",
        redirect("GET", "/web/guest", DenialReason.NO_ACCESS));
    Assert.assertEquals(
        "/o/jod-cms-auth/access-denied?reason=account-conflict",
        redirect("HEAD", "/group/guest", DenialReason.ACCOUNT_CONFLICT));
  }

  @Test
  public void testOtherMethodsAreNotRedirected() {
    Assert.assertNull(redirect("POST", "/c/portal/login", DenialReason.NO_ACCESS));
    Assert.assertNull(redirect("PUT", "/api/jsonws/invoke", DenialReason.NO_ACCESS));
  }

  @Test
  public void testAccessDeniedPageIsNotRedirected() {
    Assert.assertNull(redirect("GET", "/o/jod-cms-auth/access-denied", DenialReason.NO_ACCESS));
  }

  @Test
  public void testReasonParameterRoundTrips() {
    for (var reason : DenialReason.values()) {
      Assert.assertEquals(
          reason,
          AccessDeniedServlet.fromParameterValue(AccessDeniedServlet.toParameterValue(reason)));
    }
  }

  @Test
  public void testUnknownReasonParameterFallsBackToNoAccess() {
    Assert.assertEquals(DenialReason.NO_ACCESS, AccessDeniedServlet.fromParameterValue(null));
    Assert.assertEquals(DenialReason.NO_ACCESS, AccessDeniedServlet.fromParameterValue("other"));
  }

  private static String redirect(String method, String requestUri, DenialReason reason) {
    return CognitoAutoLogin.getAccessDeniedRedirect(
        request(method, requestUri), PATH_MODULE, reason);
  }

  private static HttpServletRequest request(String method, String requestUri) {
    return (HttpServletRequest)
        Proxy.newProxyInstance(
            HttpServletRequest.class.getClassLoader(),
            new Class<?>[] {HttpServletRequest.class},
            (proxy, invokedMethod, args) ->
                switch (invokedMethod.getName()) {
                  case "getMethod" -> method;
                  case "getRequestURI" -> requestUri;
                  default -> throw new UnsupportedOperationException(invokedMethod.getName());
                });
  }
}
