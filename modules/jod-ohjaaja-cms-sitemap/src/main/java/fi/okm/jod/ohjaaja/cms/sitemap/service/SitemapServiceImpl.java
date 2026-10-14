/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.sitemap.service;

import com.liferay.journal.model.JournalArticle;
import com.liferay.journal.service.JournalArticleLocalService;
import com.liferay.portal.kernel.dao.orm.DynamicQueryFactoryUtil;
import com.liferay.portal.kernel.dao.orm.ProjectionFactoryUtil;
import com.liferay.portal.kernel.dao.orm.PropertyFactoryUtil;
import com.liferay.portal.kernel.dao.orm.RestrictionsFactoryUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.security.permission.resource.ModelResourcePermission;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.PropsUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import fi.okm.jod.ohjaaja.cms.navigation.dto.NavigationItemDto;
import fi.okm.jod.ohjaaja.cms.navigation.service.NavigationService;
import fi.okm.jod.ohjaaja.cms.sitemap.model.Language;
import fi.okm.jod.ohjaaja.cms.sitemap.model.SitemapModel;
import fi.okm.jod.ohjaaja.cms.sitemap.xml.SitemapXmlWriter;
import fi.okm.jod.ohjaaja.cms.util.JodOhjaajaCmsUtil;
import fi.okm.jod.ohjaaja.cms.util.PermissionUtil;
import fi.okm.jod.ohjaaja.cms.util.RunAsUtil;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = SitemapService.class)
public class SitemapServiceImpl implements SitemapService {

  /** With CloudFront's max-age of 30 min a change shows within 40 min. */
  private static final Duration TIME_TO_LIVE = Duration.ofMinutes(10);

  /** How long to wait before building again, when building fails. */
  private static final Duration RETRY_AFTER_FAILURE = Duration.ofMinutes(1);

  /** How long a request waits for the first build; the build should take a few seconds. */
  private static final Duration FIRST_BUILD_WAIT = Duration.ofSeconds(30);

  private static final int QUERY_BATCH_SIZE = 500;

  private static final Log log = LogFactoryUtil.getLog(SitemapServiceImpl.class);

  @Reference private NavigationService navigationService;
  @Reference private JodOhjaajaCmsUtil jodOhjaajaCmsUtil;
  @Reference private JournalArticleLocalService journalArticleLocalService;
  @Reference private ArticleContentReader articleContentReader;

  @Reference(target = "(model.class.name=com.liferay.journal.model.JournalArticle)")
  private ModelResourcePermission<JournalArticle> journalArticleModelResourcePermission;

  private final ArticleContentRenderer renderer = new ArticleContentRenderer();
  private final ReentrantLock buildLock = new ReentrantLock();
  private volatile CachedSitemap cachedSitemap;

  /** When the first build has failed, the next one is not tried before this. */
  private volatile Instant retryBuildAfter = Instant.MIN;

  @Override
  public byte[] getSitemapXml() {
    return getCachedSitemap().sitemapXml();
  }

  @Override
  public byte[] getContentSitemapXml() {
    return getCachedSitemap().contentSitemapXml();
  }

  @Override
  public Optional<String> getContentPage(String languageCode, String externalReferenceCode) {
    var language = Language.fromCode(languageCode);
    if (language.isEmpty() || Validator.isBlank(externalReferenceCode)) {
      return Optional.empty();
    }

    var cached = getCachedSitemap();
    var articlePage = cached.model().findArticlePage(language.get(), externalReferenceCode);
    if (articlePage.isEmpty()) {
      return Optional.empty();
    }

    try {
      return RunAsUtil.callAsGuest(
          jodOhjaajaCmsUtil.getJodOhjaajaCmsGroup().getCompanyId(),
          () -> {
            // The model may be minutes old: the article must still be published in the language
            var article =
                journalArticleLocalService.fetchLatestArticle(
                    articlePage.get().resourcePrimKey(), WorkflowConstants.STATUS_APPROVED);
            if (!PermissionUtil.canView(journalArticleModelResourcePermission, article)
                || !ArrayUtil.contains(
                    article.getAvailableLanguageIds(), language.get().languageId())) {
              return Optional.empty();
            }
            var content = articleContentReader.read(article, language.get());
            return Optional.of(renderer.render(content, articlePage.get()));
          });
    } catch (PortalException | RuntimeException e) {
      // Not a 404, which would be cached as if the page did not exist
      throw new IllegalStateException(
          "Failed to render the content page of article " + externalReferenceCode, e);
    }
  }

  @Override
  public void clearCache() {
    buildLock.lock();
    try {
      cachedSitemap = null;
      retryBuildAfter = Instant.MIN;
    } finally {
      buildLock.unlock();
    }
  }

  /**
   * Builds the sitemap when it has expired. Only one request builds it: when there is a previous
   * sitemap, the other requests are served it meanwhile, and otherwise they wait for the build.
   */
  private CachedSitemap getCachedSitemap() {
    var cached = cachedSitemap;
    if (cached != null && cached.isValid()) {
      return cached;
    }
    if (cached == null) {
      // Bounded, so that a burst of requests after a restart cannot tie up the request threads
      if (!tryLock(FIRST_BUILD_WAIT)) {
        throw new IllegalStateException("The sitemap is not available yet: it is being built");
      }
    } else if (!buildLock.tryLock()) {
      return cached;
    }

    try {
      cached = cachedSitemap;
      if (cached != null && cached.isValid()) {
        return cached;
      }
      if (cached == null && Instant.now().isBefore(retryBuildAfter)) {
        throw new IllegalStateException("The sitemap is not available: building it failed");
      }
      try {
        cachedSitemap = build();
      } catch (PortalException | RuntimeException e) {
        if (cached == null) {
          retryBuildAfter = Instant.now().plus(RETRY_AFTER_FAILURE);
          throw new IllegalStateException("Failed to build the sitemap", e);
        }
        log.error("Failed to rebuild the sitemap, serving the previous one", e);
        cachedSitemap = cached.withExpiry(Instant.now().plus(RETRY_AFTER_FAILURE));
      }
      return cachedSitemap;
    } finally {
      buildLock.unlock();
    }
  }

  private boolean tryLock(Duration timeout) {
    try {
      return buildLock.tryLock(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  private CachedSitemap build() throws PortalException {
    var start = System.nanoTime();
    var frontendUrl = getFrontendUrl();
    var group = jodOhjaajaCmsUtil.getJodOhjaajaCmsGroup();

    var model =
        RunAsUtil.callAsGuest(
            group.getCompanyId(),
            () -> {
              var navigation =
                  navigationService.getNavigation(
                      group.getGroupId(), Language.DEFAULT.languageTag());
              var navigationItems =
                  navigation != null ? navigation.navigationItems() : List.<NavigationItemDto>of();
              return new SitemapModelBuilder(
                      frontendUrl,
                      getContentUrl(frontendUrl),
                      getArticleModifiedDates(getArticleIds(navigationItems)))
                  .build(navigationItems);
            });

    var cached =
        new CachedSitemap(
            model,
            SitemapXmlWriter.write(model.urls()),
            SitemapXmlWriter.write(model.contentUrls()),
            Instant.now().plus(TIME_TO_LIVE));

    log.info(
        "Built the sitemap in "
            + Duration.ofNanos(System.nanoTime() - start).toMillis()
            + " ms: "
            + model.urls().size()
            + " URLs, "
            + model.contentUrls().size()
            + " content URLs");

    return cached;
  }

  private static String getFrontendUrl() {
    var frontendUrl = PropsUtil.get("ohjaaja.frontend.url");
    if (Validator.isBlank(frontendUrl)) {
      throw new IllegalStateException("ohjaaja.frontend.url is not set");
    }
    return frontendUrl.strip().replaceAll("/+$", "");
  }

  /**
   * The content pages are served by this CMS on the same origin as the UI, under its context path
   * (/ohjaaja/cms in the AWS environments).
   */
  private static String getContentUrl(String frontendUrl) {
    var uri = URI.create(frontendUrl);
    var port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
    return uri.getScheme()
        + "://"
        + uri.getHost()
        + port
        + PortalUtil.getPathContext()
        + "/o/jod-sitemap/content";
  }

  private static Set<Long> getArticleIds(List<NavigationItemDto> navigationItems) {
    var articleIds = new HashSet<Long>();
    var stack = new ArrayList<>(navigationItems);
    while (!stack.isEmpty()) {
      var item = stack.removeLast();
      if (SitemapModelBuilder.TYPE_ARTICLE.equals(item.type()) && item.articleId() != null) {
        articleIds.add(item.articleId());
      }
      if (item.children() != null) {
        stack.addAll(item.children());
      }
    }
    return articleIds;
  }

  /**
   * The modification date of the latest approved version of each article, queried in batches
   * instead of one by one. Only the latest versions are loaded, with a subquery for the version.
   */
  private Map<Long, Date> getArticleModifiedDates(Collection<Long> resourcePrimKeys) {
    var classLoader =
        journalArticleLocalService.createJournalArticle(0).getClass().getClassLoader();
    var modifiedDates = new HashMap<Long, Date>();

    for (var batch : partition(resourcePrimKeys)) {
      var latestVersion =
          DynamicQueryFactoryUtil.forClass(JournalArticle.class, "latest", classLoader)
              .setProjection(ProjectionFactoryUtil.max("latest.version"))
              .add(
                  PropertyFactoryUtil.forName("latest.resourcePrimKey")
                      .eqProperty("this.resourcePrimKey"))
              .add(
                  PropertyFactoryUtil.forName("latest.status")
                      .eq(WorkflowConstants.STATUS_APPROVED));
      var dynamicQuery =
          journalArticleLocalService
              .dynamicQuery()
              .add(RestrictionsFactoryUtil.in("resourcePrimKey", batch))
              .add(RestrictionsFactoryUtil.eq("status", WorkflowConstants.STATUS_APPROVED))
              .add(PropertyFactoryUtil.forName("version").eq(latestVersion));
      List<JournalArticle> articles = journalArticleLocalService.dynamicQuery(dynamicQuery);
      for (var article : articles) {
        modifiedDates.put(article.getResourcePrimKey(), article.getModifiedDate());
      }
    }
    return modifiedDates;
  }

  private static List<List<Long>> partition(Collection<Long> values) {
    var list = List.copyOf(values);
    var batches = new ArrayList<List<Long>>();
    for (var i = 0; i < list.size(); i += QUERY_BATCH_SIZE) {
      batches.add(list.subList(i, Math.min(i + QUERY_BATCH_SIZE, list.size())));
    }
    return batches;
  }

  private static final class CachedSitemap {

    private final SitemapModel model;
    private final byte[] sitemapXml;
    private final byte[] contentSitemapXml;
    private final Instant expiresAt;

    CachedSitemap(
        SitemapModel model, byte[] sitemapXml, byte[] contentSitemapXml, Instant expiresAt) {
      this.model = model;
      this.sitemapXml = sitemapXml;
      this.contentSitemapXml = contentSitemapXml;
      this.expiresAt = expiresAt;
    }

    SitemapModel model() {
      return model;
    }

    byte[] sitemapXml() {
      return sitemapXml;
    }

    byte[] contentSitemapXml() {
      return contentSitemapXml;
    }

    boolean isValid() {
      return Instant.now().isBefore(expiresAt);
    }

    CachedSitemap withExpiry(Instant newExpiresAt) {
      return new CachedSitemap(model, sitemapXml, contentSitemapXml, newExpiresAt);
    }
  }
}
