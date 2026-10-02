/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.auth.jwt;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import fi.okm.jod.ohjaaja.cms.auth.configuration.CognitoAutoLoginConfiguration;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.ECPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Verifies the OIDC headers added by the AWS Application Load Balancer.
 *
 * <ul>
 *   <li>{@code x-amzn-oidc-data}: user claims signed by the ALB (ES256). The public key is fetched
 *       from the regional ALB key endpoint by key id.
 *   <li>{@code x-amzn-oidc-accesstoken}: Cognito access token (RS256), verified against the user
 *       pool JWKS. Group memberships are read from its {@code cognito:groups} claim.
 * </ul>
 */
public class OidcTokenVerifier {

  private static final Log log = LogFactoryUtil.getLog(OidcTokenVerifier.class);

  private static final Pattern KEY_ID_PATTERN = Pattern.compile("[A-Za-z0-9-]{1,128}");
  private static final Pattern REGION_PATTERN = Pattern.compile("[a-z0-9-]{1,32}");
  private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(5);

  private final CognitoAutoLoginConfiguration configuration;
  private final String issuer;
  private final DefaultJWTProcessor<SecurityContext> accessTokenProcessor;
  private final AlbPublicKeySource albPublicKeySource;
  private final Map<String, ECPublicKey> albPublicKeys = new ConcurrentHashMap<>();
  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();

  public OidcTokenVerifier(CognitoAutoLoginConfiguration configuration) {
    this(configuration, null, null);
  }

  /**
   * Uses the given key sources instead of the AWS endpoints when they are not null. Intended for
   * tests.
   */
  OidcTokenVerifier(
      CognitoAutoLoginConfiguration configuration,
      AlbPublicKeySource albPublicKeySource,
      JWKSource<SecurityContext> cognitoJwkSource) {
    if (!REGION_PATTERN.matcher(configuration.region()).matches()) {
      throw new IllegalArgumentException("Invalid AWS region: " + configuration.region());
    }
    this.configuration = configuration;
    this.issuer =
        "https://cognito-idp."
            + configuration.region()
            + ".amazonaws.com/"
            + configuration.userPoolId();
    this.albPublicKeySource =
        albPublicKeySource != null ? albPublicKeySource : this::fetchAlbPublicKey;
    this.accessTokenProcessor =
        createAccessTokenProcessor(
            cognitoJwkSource != null ? cognitoJwkSource : createCognitoJwkSource());
  }

  /**
   * Verifies both headers and returns the identity, or an empty value if either of them is missing
   * or invalid.
   */
  public Optional<OidcIdentity> verify(String oidcData, String accessToken) {
    if (oidcData == null || oidcData.isBlank() || accessToken == null || accessToken.isBlank()) {
      return Optional.empty();
    }
    try {
      var dataClaims = verifyOidcData(oidcData);
      var accessClaims = accessTokenProcessor.process(accessToken, null);

      var sub = dataClaims.getSubject();
      if (sub == null || !sub.equals(accessClaims.getSubject())) {
        log.warn("Subject of x-amzn-oidc-data does not match the access token");
        return Optional.empty();
      }
      var email = dataClaims.getStringClaim("email");
      if (email == null || email.isBlank()) {
        log.warn("x-amzn-oidc-data of subject " + sub + " has no email claim");
        return Optional.empty();
      }
      var groups = accessClaims.getStringListClaim("cognito:groups");
      return Optional.of(
          new OidcIdentity(
              sub,
              email,
              isTrue(dataClaims.getClaim("email_verified")),
              dataClaims.getStringClaim("given_name"),
              dataClaims.getStringClaim("family_name"),
              groups == null ? List.of() : List.copyOf(groups)));
    } catch (ParseException | BadJOSEException | JOSEException | OidcVerificationException e) {
      log.warn("OIDC header verification failed: " + e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * Reads the subject of {@code x-amzn-oidc-data} without verifying it. Only for detecting a change
   * of the subject cheaply, the header must be verified before acting on the change.
   */
  public static Optional<String> readUnverifiedSubject(String oidcData) {
    if (oidcData == null || oidcData.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.ofNullable(SignedJWT.parse(oidcData).getJWTClaimsSet().getSubject());
    } catch (ParseException e) {
      return Optional.empty();
    }
  }

  private JWTClaimsSet verifyOidcData(String oidcData)
      throws ParseException, JOSEException, OidcVerificationException {
    // ALB uses base64url with padding; nimbus normalizes the padding when decoding and keeps the
    // original segments as the signing input.
    var jwt = SignedJWT.parse(oidcData);
    var header = jwt.getHeader();

    if (!JWSAlgorithm.ES256.equals(header.getAlgorithm())) {
      throw new OidcVerificationException("Unexpected algorithm " + header.getAlgorithm());
    }
    if (!configuration.albArn().equals(header.getCustomParam("signer"))) {
      throw new OidcVerificationException("Unexpected signer " + header.getCustomParam("signer"));
    }
    if (!configuration.clientId().equals(header.getCustomParam("client"))) {
      throw new OidcVerificationException("Unexpected client " + header.getCustomParam("client"));
    }
    if (!jwt.verify(new ECDSAVerifier(getAlbPublicKey(header.getKeyID())))) {
      throw new OidcVerificationException("Invalid signature");
    }

    var claims = jwt.getJWTClaimsSet();
    var expiration = toInstant(header.getCustomParam("exp"));
    if (expiration == null && claims.getExpirationTime() != null) {
      expiration = claims.getExpirationTime().toInstant();
    }
    if (expiration == null
        || expiration.plusSeconds(configuration.clockSkewSeconds()).isBefore(Instant.now())) {
      throw new OidcVerificationException("Token expired");
    }
    var headerIssuer = header.getCustomParam("iss");
    if (headerIssuer != null && !issuer.equals(headerIssuer)) {
      throw new OidcVerificationException("Unexpected issuer " + headerIssuer);
    }
    return claims;
  }

  private ECPublicKey getAlbPublicKey(String keyId) throws OidcVerificationException {
    if (keyId == null || !KEY_ID_PATTERN.matcher(keyId).matches()) {
      throw new OidcVerificationException("Invalid key id");
    }
    var cached = albPublicKeys.get(keyId);
    if (cached != null) {
      return cached;
    }
    var publicKey = albPublicKeySource.get(keyId);
    albPublicKeys.put(keyId, publicKey);
    return publicKey;
  }

  private ECPublicKey fetchAlbPublicKey(String keyId) throws OidcVerificationException {
    var uri =
        URI.create(
            "https://public-keys.auth.elb." + configuration.region() + ".amazonaws.com/" + keyId);
    try {
      var response =
          httpClient.send(
              HttpRequest.newBuilder(uri).timeout(HTTP_TIMEOUT).GET().build(),
              HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new OidcVerificationException(
            "Fetching ALB public key failed with status " + response.statusCode());
      }
      var base64 =
          response.body().replaceAll("-----(BEGIN|END) PUBLIC KEY-----", "").replaceAll("\\s", "");
      var keySpec = new X509EncodedKeySpec(Base64.getDecoder().decode(base64));
      return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(keySpec);
    } catch (IOException | GeneralSecurityException | IllegalArgumentException e) {
      throw new OidcVerificationException("Fetching ALB public key failed: " + e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new OidcVerificationException("Fetching ALB public key interrupted");
    }
  }

  private JWKSource<SecurityContext> createCognitoJwkSource() {
    try {
      return JWKSourceBuilder.create(URI.create(issuer + "/.well-known/jwks.json").toURL())
          .retrying(true)
          .build();
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException("Invalid Cognito JWKS URL", e);
    }
  }

  private DefaultJWTProcessor<SecurityContext> createAccessTokenProcessor(
      JWKSource<SecurityContext> jwkSource) {
    var processor = new DefaultJWTProcessor<>();
    processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource));
    var claimsVerifier =
        new DefaultJWTClaimsVerifier<>(
            new JWTClaimsSet.Builder()
                .issuer(issuer)
                .claim("token_use", "access")
                .claim("client_id", configuration.clientId())
                .build(),
            Set.of("sub", "exp"));
    claimsVerifier.setMaxClockSkew(configuration.clockSkewSeconds());
    processor.setJWTClaimsSetVerifier(claimsVerifier);
    return processor;
  }

  /** Cognito UserInfo returns {@code email_verified} as a string, ID tokens as a boolean. */
  private static boolean isTrue(Object claim) {
    return Boolean.TRUE.equals(claim) || "true".equals(claim);
  }

  private static Instant toInstant(Object epochSeconds) {
    if (epochSeconds instanceof Number number) {
      return Instant.ofEpochSecond(number.longValue());
    }
    return null;
  }

  /** Source of the ALB public keys by key id. */
  @FunctionalInterface
  interface AlbPublicKeySource {
    ECPublicKey get(String keyId) throws OidcVerificationException;
  }

  /** Verification failure that is logged without the token contents. */
  static final class OidcVerificationException extends Exception {
    OidcVerificationException(String message) {
      super(Objects.requireNonNull(message));
    }
  }
}
