/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.auth.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import fi.okm.jod.ohjaaja.cms.auth.configuration.CognitoAutoLoginConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/** Unit tests for verifying the ALB OIDC headers with locally generated keys. */
public class OidcTokenVerifierTest {

  private static final String REGION = "eu-west-1";
  private static final String USER_POOL_ID = "eu-west-1_test";
  private static final String CLIENT_ID = "test-client";
  private static final String ALB_ARN =
      "arn:aws:elasticloadbalancing:eu-west-1:123456789012:loadbalancer/app/test/abc";
  private static final String ISSUER =
      "https://cognito-idp." + REGION + ".amazonaws.com/" + USER_POOL_ID;
  private static final String ALB_KEY_ID = "alb-key";
  private static final String SUB = "8c6d2b3e-0000-4000-8000-000000000001";

  private static ECKey albKey;
  private static ECKey otherAlbKey;
  private static RSAKey cognitoKey;
  private static RSAKey otherCognitoKey;

  private OidcTokenVerifier verifier = newVerifier(albKey);

  @BeforeClass
  public static void setUpClass() throws Exception {
    albKey = new ECKeyGenerator(Curve.P_256).keyID(ALB_KEY_ID).generate();
    otherAlbKey = new ECKeyGenerator(Curve.P_256).keyID(ALB_KEY_ID).generate();
    cognitoKey = new RSAKeyGenerator(2048).keyID("cognito-key").generate();
    otherCognitoKey = new RSAKeyGenerator(2048).keyID("cognito-key").generate();
  }

  @Test
  public void testValidHeadersReturnIdentity() throws Exception {
    var identity = verify(oidcData(header -> {}, claims -> {}), accessToken(claims -> {}));

    Assert.assertTrue(identity.isPresent());
    Assert.assertEquals(SUB, identity.get().sub());
    Assert.assertEquals("matti.meikalainen@example.com", identity.get().email());
    Assert.assertTrue(identity.get().emailVerified());
    Assert.assertEquals("Matti", identity.get().givenName());
    Assert.assertEquals("Meikäläinen", identity.get().familyName());
    Assert.assertEquals(List.of("cms-admin", "other"), identity.get().groups());
  }

  @Test
  public void testBooleanEmailVerifiedClaimIsAccepted() throws Exception {
    var identity =
        verify(
            oidcData(header -> {}, claims -> claims.claim("email_verified", true)),
            accessToken(claims -> {}));

    Assert.assertTrue(identity.orElseThrow().emailVerified());
  }

  @Test
  public void testUnverifiedOrMissingEmailVerifiedClaimIsNotVerified() throws Exception {
    for (var emailVerified : new Object[] {"false", false, null}) {
      var identity =
          verify(
              oidcData(header -> {}, claims -> claims.claim("email_verified", emailVerified)),
              accessToken(claims -> {}));

      Assert.assertFalse(identity.orElseThrow().emailVerified());
    }
  }

  @Test
  public void testMissingGroupsClaimReturnsEmptyGroups() throws Exception {
    var identity =
        verify(
            oidcData(header -> {}, claims -> {}),
            accessToken(claims -> claims.claim("cognito:groups", null)));

    Assert.assertEquals(List.of(), identity.orElseThrow().groups());
  }

  @Test
  public void testMissingHeadersAreRejected() throws Exception {
    var oidcData = oidcData(header -> {}, claims -> {});
    var accessToken = accessToken(claims -> {});

    Assert.assertTrue(verifier.verify(null, accessToken).isEmpty());
    Assert.assertTrue(verifier.verify(oidcData, null).isEmpty());
    Assert.assertTrue(verifier.verify(" ", accessToken).isEmpty());
    Assert.assertTrue(verifier.verify("not-a-jwt", accessToken).isEmpty());
  }

  @Test
  public void testOidcDataSignedWithOtherKeyIsRejected() throws Exception {
    verifier = newVerifier(otherAlbKey);

    Assert.assertTrue(
        verify(oidcData(header -> {}, claims -> {}), accessToken(claims -> {})).isEmpty());
  }

  @Test
  public void testOidcDataWithUnexpectedSignerIsRejected() throws Exception {
    var oidcData =
        oidcData(header -> header.customParam("signer", ALB_ARN + "-other"), claims -> {});

    Assert.assertTrue(verify(oidcData, accessToken(claims -> {})).isEmpty());
  }

  @Test
  public void testOidcDataWithUnexpectedClientIsRejected() throws Exception {
    var oidcData = oidcData(header -> header.customParam("client", "other-client"), claims -> {});

    Assert.assertTrue(verify(oidcData, accessToken(claims -> {})).isEmpty());
  }

  @Test
  public void testOidcDataWithUnexpectedIssuerIsRejected() throws Exception {
    var oidcData = oidcData(header -> header.customParam("iss", ISSUER + "-other"), claims -> {});

    Assert.assertTrue(verify(oidcData, accessToken(claims -> {})).isEmpty());
  }

  @Test
  public void testExpiredOidcDataIsRejected() throws Exception {
    var oidcData =
        oidcData(
            header -> header.customParam("exp", Instant.now().minusSeconds(120).getEpochSecond()),
            claims -> {});

    Assert.assertTrue(verify(oidcData, accessToken(claims -> {})).isEmpty());
  }

  @Test
  public void testOidcDataWithoutEmailIsRejected() throws Exception {
    var oidcData = oidcData(header -> {}, claims -> claims.claim("email", null));

    Assert.assertTrue(verify(oidcData, accessToken(claims -> {})).isEmpty());
  }

  @Test
  public void testSubjectMismatchIsRejected() throws Exception {
    var accessToken = accessToken(claims -> claims.subject("other-subject"));

    Assert.assertTrue(verify(oidcData(header -> {}, claims -> {}), accessToken).isEmpty());
  }

  @Test
  public void testAccessTokenSignedWithOtherKeyIsRejected() throws Exception {
    var accessToken = signAccessToken(otherCognitoKey, accessTokenClaims(claims -> {}));

    Assert.assertTrue(verify(oidcData(header -> {}, claims -> {}), accessToken).isEmpty());
  }

  @Test
  public void testAccessTokenWithUnexpectedIssuerIsRejected() throws Exception {
    var accessToken = accessToken(claims -> claims.issuer(ISSUER + "-other"));

    Assert.assertTrue(verify(oidcData(header -> {}, claims -> {}), accessToken).isEmpty());
  }

  @Test
  public void testIdTokenAsAccessTokenIsRejected() throws Exception {
    var accessToken = accessToken(claims -> claims.claim("token_use", "id"));

    Assert.assertTrue(verify(oidcData(header -> {}, claims -> {}), accessToken).isEmpty());
  }

  @Test
  public void testAccessTokenOfOtherClientIsRejected() throws Exception {
    var accessToken = accessToken(claims -> claims.claim("client_id", "other-client"));

    Assert.assertTrue(verify(oidcData(header -> {}, claims -> {}), accessToken).isEmpty());
  }

  @Test
  public void testExpiredAccessTokenIsRejected() throws Exception {
    var accessToken =
        accessToken(claims -> claims.expirationTime(Date.from(Instant.now().minusSeconds(120))));

    Assert.assertTrue(verify(oidcData(header -> {}, claims -> {}), accessToken).isEmpty());
  }

  @Test
  public void testAlbPublicKeyIsCached() throws Exception {
    var fetches = new AtomicInteger();
    var publicKey = albKey.toECPublicKey();
    verifier =
        new OidcTokenVerifier(
            new TestConfiguration(),
            keyId -> {
              fetches.incrementAndGet();
              return publicKey;
            },
            new ImmutableJWKSet<>(new JWKSet(cognitoKey.toPublicJWK())));

    verify(oidcData(header -> {}, claims -> {}), accessToken(claims -> {}));
    verify(oidcData(header -> {}, claims -> {}), accessToken(claims -> {}));

    Assert.assertEquals(1, fetches.get());
  }

  @Test
  public void testUnverifiedSubjectIsReadWithoutValidSignature() throws Exception {
    verifier = newVerifier(otherAlbKey);
    var oidcData = oidcData(header -> {}, claims -> {});

    Assert.assertTrue(verify(oidcData, accessToken(claims -> {})).isEmpty());
    Assert.assertEquals(Optional.of(SUB), OidcTokenVerifier.readUnverifiedSubject(oidcData));
  }

  @Test
  public void testUnverifiedSubjectOfMissingOrInvalidHeaderIsEmpty() throws Exception {
    Assert.assertTrue(OidcTokenVerifier.readUnverifiedSubject(null).isEmpty());
    Assert.assertTrue(OidcTokenVerifier.readUnverifiedSubject(" ").isEmpty());
    Assert.assertTrue(OidcTokenVerifier.readUnverifiedSubject("not-a-jwt").isEmpty());
    Assert.assertTrue(
        OidcTokenVerifier.readUnverifiedSubject(
                oidcData(header -> {}, claims -> claims.subject(null)))
            .isEmpty());
  }

  private Optional<OidcIdentity> verify(String oidcData, String accessToken) {
    return verifier.verify(oidcData, accessToken);
  }

  private static OidcTokenVerifier newVerifier(ECKey albVerificationKey) {
    try {
      var publicKey = albVerificationKey.toECPublicKey();
      return new OidcTokenVerifier(
          new TestConfiguration(),
          keyId -> publicKey,
          new ImmutableJWKSet<>(new JWKSet(cognitoKey.toPublicJWK())));
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Builds x-amzn-oidc-data like the ALB does: ES256 with custom header fields and base64url
   * segments that keep their padding.
   */
  private static String oidcData(
      Consumer<JWSHeader.Builder> headerCustomizer, Consumer<JWTClaimsSet.Builder> claimsCustomizer)
      throws Exception {
    var headerBuilder =
        new JWSHeader.Builder(JWSAlgorithm.ES256)
            .keyID(ALB_KEY_ID)
            .customParam("signer", ALB_ARN)
            .customParam("client", CLIENT_ID)
            .customParam("iss", ISSUER)
            .customParam("exp", Instant.now().plusSeconds(60).getEpochSecond());
    headerCustomizer.accept(headerBuilder);
    var header = headerBuilder.build();

    var claimsBuilder =
        new JWTClaimsSet.Builder()
            .subject(SUB)
            .claim("email", "matti.meikalainen@example.com")
            .claim("email_verified", "true")
            .claim("given_name", "Matti")
            .claim("family_name", "Meikäläinen");
    claimsCustomizer.accept(claimsBuilder);

    var signingInput =
        paddedBase64Url(header.toString())
            + "."
            + paddedBase64Url(claimsBuilder.build().toString());
    var signature =
        new ECDSASigner(albKey).sign(header, signingInput.getBytes(StandardCharsets.US_ASCII));
    return signingInput + "." + signature;
  }

  private static String accessToken(Consumer<JWTClaimsSet.Builder> claimsCustomizer)
      throws Exception {
    return signAccessToken(cognitoKey, accessTokenClaims(claimsCustomizer));
  }

  private static JWTClaimsSet accessTokenClaims(Consumer<JWTClaimsSet.Builder> claimsCustomizer) {
    var claimsBuilder =
        new JWTClaimsSet.Builder()
            .issuer(ISSUER)
            .subject(SUB)
            .expirationTime(Date.from(Instant.now().plusSeconds(60)))
            .claim("token_use", "access")
            .claim("client_id", CLIENT_ID)
            .claim("cognito:groups", List.of("cms-admin", "other"));
    claimsCustomizer.accept(claimsBuilder);
    return claimsBuilder.build();
  }

  private static String signAccessToken(RSAKey key, JWTClaimsSet claims) throws Exception {
    var jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
    jwt.sign(new RSASSASigner(key));
    return jwt.serialize();
  }

  private static String paddedBase64Url(String json) {
    return Base64.getUrlEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
  }

  private static final class TestConfiguration implements CognitoAutoLoginConfiguration {

    @Override
    public boolean enabled() {
      return true;
    }

    @Override
    public String region() {
      return REGION;
    }

    @Override
    public String userPoolId() {
      return USER_POOL_ID;
    }

    @Override
    public String clientId() {
      return CLIENT_ID;
    }

    @Override
    public String albArn() {
      return ALB_ARN;
    }

    @Override
    public int clockSkewSeconds() {
      return 60;
    }

    @Override
    public String logoutUrl() {
      return "";
    }
  }
}
