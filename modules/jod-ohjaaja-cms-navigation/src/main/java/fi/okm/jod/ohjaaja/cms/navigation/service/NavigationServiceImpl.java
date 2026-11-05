/*
 * Copyright (c) 2025 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.navigation.service;

import com.liferay.asset.kernel.model.AssetCategory;
import com.liferay.asset.kernel.service.AssetCategoryLocalService;
import com.liferay.expando.kernel.model.ExpandoBridge;
import com.liferay.expando.kernel.model.ExpandoColumn;
import com.liferay.expando.kernel.model.ExpandoColumnConstants;
import com.liferay.expando.kernel.model.ExpandoTableConstants;
import com.liferay.expando.kernel.service.ExpandoColumnLocalServiceUtil;
import com.liferay.expando.kernel.service.ExpandoValueLocalServiceUtil;
import com.liferay.expando.kernel.util.ExpandoBridgeFactoryUtil;
import com.liferay.journal.model.JournalArticle;
import com.liferay.journal.service.JournalArticleLocalService;
import com.liferay.portal.kernel.cache.MultiVMPool;
import com.liferay.portal.kernel.cache.PortalCache;
import com.liferay.portal.kernel.cache.PortalCacheHelperUtil;
import com.liferay.portal.kernel.cache.PortalCacheListener;
import com.liferay.portal.kernel.cache.PortalCacheListenerScope;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.ResourceConstants;
import com.liferay.portal.kernel.model.role.RoleConstants;
import com.liferay.portal.kernel.security.auth.PrincipalThreadLocal;
import com.liferay.portal.kernel.security.permission.ActionKeys;
import com.liferay.portal.kernel.security.permission.ResourceActionsUtil;
import com.liferay.portal.kernel.security.permission.resource.ModelResourcePermission;
import com.liferay.portal.kernel.service.ResourcePermissionLocalServiceUtil;
import com.liferay.portal.kernel.service.RoleLocalServiceUtil;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.transaction.TransactionCommitCallbackUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.UnicodeProperties;
import com.liferay.portal.kernel.util.UnicodePropertiesBuilder;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.portal.language.override.service.PLOEntryLocalService;
import com.liferay.portal.vulcan.util.LocalizedMapUtil;
import com.liferay.site.navigation.model.SiteNavigationMenu;
import com.liferay.site.navigation.model.SiteNavigationMenuItem;
import com.liferay.site.navigation.service.SiteNavigationMenuItemLocalService;
import com.liferay.site.navigation.service.SiteNavigationMenuLocalService;
import com.liferay.site.navigation.service.SiteNavigationMenuService;
import com.liferay.site.navigation.util.comparator.SiteNavigationMenuItemOrderComparator;
import fi.okm.jod.ohjaaja.cms.navigation.dto.NavigationDto;
import fi.okm.jod.ohjaaja.cms.navigation.dto.NavigationItemDto;
import fi.okm.jod.ohjaaja.cms.navigation.exception.MultipleStudyProgramListingMenuItemExpection;
import fi.okm.jod.ohjaaja.cms.navigation.exception.NavigationInitializationException;
import fi.okm.jod.ohjaaja.cms.navigation.exception.NavigationMenuItemPersistenceException;
import fi.okm.jod.ohjaaja.cms.navigation.exception.StudyProgramListingMissingException;
import fi.okm.jod.ohjaaja.cms.util.AdminUtil;
import fi.okm.jod.ohjaaja.cms.util.JodOhjaajaCmsUtil;
import fi.okm.jod.ohjaaja.cms.util.PermissionUtil;
import fi.okm.jod.ohjaaja.cms.util.RunAsUtil;
import fi.okm.jod.ohjaaja.cms.util.SlugUtil;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

@Component(immediate = true, service = NavigationService.class)
public class NavigationServiceImpl implements NavigationService {

  private static final String LANGUAGE_ID_EN_US = "en_US";
  private static final String LANGUAGE_ID_FI_FI = "fi_FI";

  private static final String PROPERTY_CLASS_PK = "classPK";
  private static final String PROPERTY_EXTERNAL_REFERENCE_CODE = "externalReferenceCode";
  private static final String PROPERTY_TITLE = "title";

  private static final String ASSET_CATEGORY_CLASS_NAME =
      "com.liferay.asset.kernel.model.AssetCategory";
  private static final String JOURNAL_ARTICLE_CLASS_NAME =
      "com.liferay.journal.model.JournalArticle";

  private static final String TYPE_CUSTOM_FIELD_NAME = "Type";
  private static final String TYPE_CATEGORY_MAIN = "CategoryMain";
  private static final String TYPE_STUDY_PROGRAMS_LISTING = "StudyProgramsListing";

  private static final String NAVIGATION_MENU_NAME = "JOD Ohjaaja";
  private static final String HIDE_FROM_HOME_PAGE_NEWEST_CAROUSEL_FIELD_NAME =
      "hideFromHomePageNewestCarousel";
  private static final String HIDE_FROM_HOME_PAGE_MOST_VIEWED_CAROUSEL_FIELD_NAME =
      "hideFromHomePageMostViewedCarousel";
  private static final String HIDE_FROM_MAIN_CATEGORY_PAGE_NEWEST_CAROUSEL_FIELD_NAME =
      "hideFromMainCategoryPageNewestCarousel";
  private static final String HIDE_FROM_MAIN_CATEGORY_PAGE_MOST_VIEWED_CAROUSEL_FIELD_NAME =
      "hideFromMainCategoryPageMostViewedCarousel";

  private static final CustomFieldData[] CUSTOM_FIELDS = {
    new CustomFieldData(
        TYPE_CUSTOM_FIELD_NAME,
        ExpandoColumnConstants.STRING_ARRAY,
        new String[] {
          "Article", "CategoryListing", TYPE_CATEGORY_MAIN, TYPE_STUDY_PROGRAMS_LISTING
        },
        ExpandoColumnConstants.PROPERTY_DISPLAY_TYPE_SELECTION_LIST,
        Map.of(LANGUAGE_ID_FI_FI, "Tyyppi", LANGUAGE_ID_EN_US, "Type")),
    new CustomFieldData(
        HIDE_FROM_HOME_PAGE_NEWEST_CAROUSEL_FIELD_NAME,
        ExpandoColumnConstants.BOOLEAN,
        false,
        ExpandoColumnConstants.PROPERTY_DISPLAY_TYPE_CHECKBOX,
        Map.of(
            LANGUAGE_ID_FI_FI,
            "Piilota uusien karusellista etusivulta",
            LANGUAGE_ID_EN_US,
            "Hide from the homepage new items carousel")),
    new CustomFieldData(
        HIDE_FROM_HOME_PAGE_MOST_VIEWED_CAROUSEL_FIELD_NAME,
        ExpandoColumnConstants.BOOLEAN,
        false,
        ExpandoColumnConstants.PROPERTY_DISPLAY_TYPE_CHECKBOX,
        Map.of(
            LANGUAGE_ID_FI_FI,
            "Piilota suosittujen karusellista etusivulta",
            LANGUAGE_ID_EN_US,
            "Hide from the homepage most viewed carousel")),
    new CustomFieldData(
        HIDE_FROM_MAIN_CATEGORY_PAGE_NEWEST_CAROUSEL_FIELD_NAME,
        ExpandoColumnConstants.BOOLEAN,
        false,
        ExpandoColumnConstants.PROPERTY_DISPLAY_TYPE_CHECKBOX,
        Map.of(
            LANGUAGE_ID_FI_FI,
            "Piilota uusien karusellista pääkategoriasivulta",
            LANGUAGE_ID_EN_US,
            "Hide from the main category page new items carousel")),
    new CustomFieldData(
        HIDE_FROM_MAIN_CATEGORY_PAGE_MOST_VIEWED_CAROUSEL_FIELD_NAME,
        ExpandoColumnConstants.BOOLEAN,
        false,
        ExpandoColumnConstants.PROPERTY_DISPLAY_TYPE_CHECKBOX,
        Map.of(
            LANGUAGE_ID_FI_FI,
            "Piilota suosittujen karusellista pääkategoriasivulta",
            LANGUAGE_ID_EN_US,
            "Hide from the main category page most viewed carousel"))
  };

  private static final String NAVIGATION_CACHE_NAME =
      NavigationServiceImpl.class.getName() + ".navigation";
  private static final String NAVIGATION_CACHE_KEY = "navigation";
  // A safety net for changes that are not covered by the cache invalidating model listeners. The
  // cache can also be cleared in Control Panel > Server Administration > Resources > Clear content
  // cached across the cluster.
  private static final int NAVIGATION_CACHE_TIME_TO_LIVE = (int) Duration.ofHours(24).toSeconds();
  private static final Duration BUILD_LOCK_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration RETRY_INITIALIZATION_AFTER = Duration.ofMinutes(1);

  private static final Log log = LogFactoryUtil.getLog(NavigationServiceImpl.class);

  @Reference private SiteNavigationMenuItemLocalService siteNavigationMenuItemLocalService;
  @Reference private SiteNavigationMenuService siteNavigationMenuService;
  @Reference private SiteNavigationMenuLocalService siteNavigationMenuLocalService;
  @Reference private AssetCategoryLocalService assetCategoryLocalService;
  @Reference private MainCategoryService mainCategoryService;
  @Reference private JournalArticleLocalService journalArticleLocalService;

  @Reference(target = "(model.class.name=com.liferay.asset.kernel.model.AssetCategory)")
  private ModelResourcePermission<AssetCategory> assetCategoryModelResourcePermission;

  @Reference(target = "(model.class.name=com.liferay.journal.model.JournalArticle)")
  private ModelResourcePermission<JournalArticle> journalArticleModelResourcePermission;

  @Reference private PLOEntryLocalService ploEntryLocalService;
  @Reference private JodOhjaajaCmsUtil jodOhjaajaCmsUtil;
  @Reference private MultiVMPool multiVMPool;

  private PortalCache<String, NavigationDto> navigationCache;
  // Incremented whenever the cache is cleared, also by another cluster node, so that a navigation
  // built from the data before the clear is not cached
  private final AtomicLong navigationCacheGeneration = new AtomicLong();
  private final ReentrantLock buildLock = new ReentrantLock();
  private volatile Instant retryInitializationAfter = Instant.MIN;

  @Activate
  @SuppressWarnings("unchecked")
  protected void activate() {
    navigationCache =
        (PortalCache<String, NavigationDto>) multiVMPool.getPortalCache(NAVIGATION_CACHE_NAME);
    navigationCache.registerPortalCacheListener(
        new NavigationCacheRemoveAllListener(), PortalCacheListenerScope.ALL);
  }

  @Deactivate
  protected void deactivate() {
    multiVMPool.removePortalCache(NAVIGATION_CACHE_NAME);
  }

  // The navigation is only used by the public UI, so it is always built with the guest's
  // permissions and the same navigation can be cached for everyone
  @Override
  public NavigationDto getNavigation() {
    var cachedNavigation = navigationCache.get(NAVIGATION_CACHE_KEY);
    if (cachedNavigation != null) {
      return cachedNavigation;
    }

    // Bounded, so that a stuck build cannot tie up the request threads
    if (!tryLockBuild()) {
      return buildNavigationAsGuest();
    }
    try {
      cachedNavigation = navigationCache.get(NAVIGATION_CACHE_KEY);
      if (cachedNavigation != null) {
        return cachedNavigation;
      }

      var generation = navigationCacheGeneration.get();
      var navigation = buildNavigationAsGuest();
      if (navigation != null && generation == navigationCacheGeneration.get()) {
        PortalCacheHelperUtil.putWithoutReplicator(
            navigationCache, NAVIGATION_CACHE_KEY, navigation, NAVIGATION_CACHE_TIME_TO_LIVE);
        // The cache may have been cleared between the check and the put
        if (generation != navigationCacheGeneration.get()) {
          PortalCacheHelperUtil.removeWithoutReplicator(navigationCache, NAVIGATION_CACHE_KEY);
        }
      }
      return navigation;
    } finally {
      buildLock.unlock();
    }
  }

  private boolean tryLockBuild() {
    try {
      return buildLock.tryLock(BUILD_LOCK_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  // Cleared after the commit: until then the other transactions still see the old data
  @Override
  public void clearNavigationCache() {
    TransactionCommitCallbackUtil.registerCallback(
        () -> {
          navigationCache.removeAll();
          return null;
        });
  }

  private NavigationDto buildNavigationAsGuest() {
    var group = jodOhjaajaCmsUtil.getJodOhjaajaCmsGroup();
    retryNavigationInitialization(group);
    try {
      return RunAsUtil.callAsGuest(
          group.getCompanyId(),
          () -> {
            var siteNavigationMenus =
                siteNavigationMenuService.getSiteNavigationMenus(group.getGroupId());
            // The UI uses the localized fields, the plain fields are in the site's default language
            var languageId = LocaleUtil.toLanguageId(PortalUtil.getSiteDefaultLocale(group));
            // Only the managed menu, which the guest must be able to view
            var siteNavigationMenu =
                siteNavigationMenus.stream()
                    .filter(
                        menu ->
                            NAVIGATION_MENU_EXTERNAL_REFERENCE_CODE.equals(
                                menu.getExternalReferenceCode()))
                    .findFirst()
                    .orElse(null);
            return siteNavigationMenu == null
                ? null
                : toNavigationDto(siteNavigationMenu, languageId);
          });
    } catch (PortalException e) {
      throw new SystemException("Failed to build the navigation", e);
    }
  }

  private class NavigationCacheRemoveAllListener
      implements PortalCacheListener<String, NavigationDto> {

    @Override
    public void notifyRemoveAll(PortalCache<String, NavigationDto> portalCache) {
      navigationCacheGeneration.incrementAndGet();
    }

    @Override
    public void notifyEntryEvicted(
        PortalCache<String, NavigationDto> portalCache,
        String key,
        NavigationDto value,
        int timeToLive) {
      // Only the clears are relevant, see notifyRemoveAll
    }

    @Override
    public void notifyEntryExpired(
        PortalCache<String, NavigationDto> portalCache,
        String key,
        NavigationDto value,
        int timeToLive) {
      // Only the clears are relevant, see notifyRemoveAll
    }

    @Override
    public void notifyEntryPut(
        PortalCache<String, NavigationDto> portalCache,
        String key,
        NavigationDto value,
        int timeToLive) {
      // Only the clears are relevant, see notifyRemoveAll
    }

    @Override
    public void notifyEntryRemoved(
        PortalCache<String, NavigationDto> portalCache,
        String key,
        NavigationDto value,
        int timeToLive) {
      // Only the clears are relevant, see notifyRemoveAll
    }

    @Override
    public void notifyEntryUpdated(
        PortalCache<String, NavigationDto> portalCache,
        String key,
        NavigationDto value,
        int timeToLive) {
      // Only the clears are relevant, see notifyRemoveAll
    }

    @Override
    public void dispose() {
      // Nothing to release
    }
  }

  @Override
  public void initNavigation() {
    initCustomFields();
    var siteNavigationMenu = initNavigationMenu();
    if (siteNavigationMenu != null) {
      initMainCategoryMenuItems(siteNavigationMenu);
    }
  }

  /**
   * The main categories are the top level of the navigation, in order. See {@link
   * #getOrCreateMainCategoryMenuItem}.
   */
  private void initMainCategoryMenuItems(SiteNavigationMenu siteNavigationMenu) {
    var group = jodOhjaajaCmsUtil.getJodOhjaajaCmsGroup();
    try {
      AdminUtil.runAsAdmin(
          group.getCompanyId(),
          () -> {
            var serviceContext = createServiceContext(group, PrincipalThreadLocal.getUserId());
            for (var mainCategory : mainCategoryService.initMainCategories()) {
              try {
                getOrCreateMainCategoryMenuItem(siteNavigationMenu, mainCategory, serviceContext);
              } catch (PortalException e) {
                log.error(e.getMessage(), e);
              }
            }
          });
    } catch (Exception e) {
      log.error("Failed to initialize the main categories of the navigation", e);
    }
  }

  /**
   * The menu item of a main category has the external reference code of the category. Once it
   * exists, it is used as it is, also if it was moved or edited in the UI. A menu item created by
   * hand for the category is taken over, one at the top level first, and a missing one is added at
   * the end of the top level.
   */
  private synchronized SiteNavigationMenuItem getOrCreateMainCategoryMenuItem(
      SiteNavigationMenu siteNavigationMenu,
      AssetCategory mainCategory,
      ServiceContext serviceContext)
      throws PortalException {
    var externalReferenceCode = mainCategory.getExternalReferenceCode();
    var menuItem = fetchMenuItem(externalReferenceCode, siteNavigationMenu.getGroupId());
    if (menuItem != null) {
      if (menuItem.getSiteNavigationMenuId() != siteNavigationMenu.getSiteNavigationMenuId()) {
        throw new PortalException(
            "Menu item "
                + externalReferenceCode
                + " ("
                + menuItem.getSiteNavigationMenuItemId()
                + ") is not in the navigation menu "
                + NAVIGATION_MENU_EXTERNAL_REFERENCE_CODE);
      }
      return menuItem;
    }

    menuItem =
        getMenuItems(siteNavigationMenu).stream()
            .filter(item -> isMenuItemOfCategory(item, mainCategory))
            .min(
                Comparator.comparing(
                    (SiteNavigationMenuItem item) -> item.getParentSiteNavigationMenuItemId() != 0))
            .orElse(null);
    if (menuItem != null) {
      return takeOverMenuItem(menuItem, externalReferenceCode);
    }

    return addCategoryMenuItem(
        externalReferenceCode,
        siteNavigationMenu,
        0,
        mainCategory,
        TYPE_CATEGORY_MAIN,
        serviceContext);
  }

  private List<SiteNavigationMenuItem> getMenuItems(SiteNavigationMenu siteNavigationMenu) {
    return siteNavigationMenuItemLocalService.getSiteNavigationMenuItems(
        siteNavigationMenu.getSiteNavigationMenuId(),
        SiteNavigationMenuItemOrderComparator.getInstance(true));
  }

  private SiteNavigationMenuItem takeOverMenuItem(
      SiteNavigationMenuItem menuItem, String externalReferenceCode) {
    var previousExternalReferenceCode = menuItem.getExternalReferenceCode();
    menuItem.setExternalReferenceCode(externalReferenceCode);
    menuItem = siteNavigationMenuItemLocalService.updateSiteNavigationMenuItem(menuItem);
    log.info(
        "Took over menu item "
            + menuItem.getSiteNavigationMenuItemId()
            + ", changed its external reference code from "
            + previousExternalReferenceCode
            + " to "
            + externalReferenceCode);
    return menuItem;
  }

  /**
   * The menu is initialized on startup, but if that failed, e.g. because of a database error, the
   * navigation would be empty until the next restart. The navigation is cached, so this is tried
   * only while the menu is missing, and at most once a minute so that a failing initialization does
   * not hold up every request.
   */
  private void retryNavigationInitialization(Group group) {
    if (fetchNavigationMenu(group.getGroupId()) != null
        || Instant.now().isBefore(retryInitializationAfter)) {
      return;
    }
    synchronized (this) {
      if (fetchNavigationMenu(group.getGroupId()) != null
          || Instant.now().isBefore(retryInitializationAfter)) {
        return;
      }
      retryInitializationAfter = Instant.now().plus(RETRY_INITIALIZATION_AFTER);
      log.warn(
          "Navigation menu " + NAVIGATION_MENU_EXTERNAL_REFERENCE_CODE + " missing, initializing");
      var siteNavigationMenu = initNavigationMenu();
      if (siteNavigationMenu != null) {
        initMainCategoryMenuItems(siteNavigationMenu);
      }
    }
  }

  private SiteNavigationMenu fetchNavigationMenu(long groupId) {
    return siteNavigationMenuLocalService.fetchSiteNavigationMenuByExternalReferenceCode(
        NAVIGATION_MENU_EXTERNAL_REFERENCE_CODE, groupId);
  }

  // A missing menu does not prevent the navigation API from starting, it only returns no navigation
  private SiteNavigationMenu initNavigationMenu() {
    var group = jodOhjaajaCmsUtil.getJodOhjaajaCmsGroup();
    try {
      var adminUser = AdminUtil.getAdminUser(group.getCompanyId());
      return RunAsUtil.callAs(
          adminUser, () -> getOrCreateNavigationMenu(group, adminUser.getUserId()));
    } catch (Exception e) {
      log.error("Failed to initialize the navigation menu", e);
      return null;
    }
  }

  private SiteNavigationMenuItem fetchMenuItem(String externalReferenceCode, long groupId) {
    return siteNavigationMenuItemLocalService.fetchSiteNavigationMenuItemByExternalReferenceCode(
        externalReferenceCode, groupId);
  }

  // The startup, the first request and the import may create the menu and its managed items at the
  // same time, so they are created under the lock of this service
  private synchronized SiteNavigationMenu getOrCreateNavigationMenu(Group group, long userId)
      throws PortalException {
    var groupId = group.getGroupId();
    var siteNavigationMenu =
        siteNavigationMenuLocalService.fetchSiteNavigationMenuByExternalReferenceCode(
            NAVIGATION_MENU_EXTERNAL_REFERENCE_CODE, groupId);
    if (siteNavigationMenu != null) {
      return siteNavigationMenu;
    }

    var siteNavigationMenus = siteNavigationMenuLocalService.getSiteNavigationMenus(groupId);
    if (!siteNavigationMenus.isEmpty()) {
      siteNavigationMenu = adoptNavigationMenu(group, siteNavigationMenus);
      if (siteNavigationMenu != null) {
        return siteNavigationMenu;
      }
    }

    siteNavigationMenu =
        siteNavigationMenuLocalService.addSiteNavigationMenu(
            NAVIGATION_MENU_EXTERNAL_REFERENCE_CODE,
            userId,
            groupId,
            NAVIGATION_MENU_NAME,
            createServiceContext(group, userId));
    log.info("Created navigation menu " + NAVIGATION_MENU_EXTERNAL_REFERENCE_CODE);
    return siteNavigationMenu;
  }

  /**
   * Takes over the menu the public navigation has shown so far: the first one visible to the guest.
   * If there is none, e.g. only menus of the admin UI, none is taken over.
   */
  private SiteNavigationMenu adoptNavigationMenu(
      Group group, List<SiteNavigationMenu> siteNavigationMenus) throws PortalException {
    var guestSiteNavigationMenus =
        RunAsUtil.callAsGuest(
            group.getCompanyId(),
            () -> siteNavigationMenuService.getSiteNavigationMenus(group.getGroupId()));
    if (guestSiteNavigationMenus.isEmpty()) {
      return null;
    }
    var siteNavigationMenu = guestSiteNavigationMenus.getFirst();
    if (siteNavigationMenus.size() > 1) {
      log.warn(
          "The site has "
              + siteNavigationMenus.size()
              + " navigation menus, using "
              + siteNavigationMenu.getName()
              + " ("
              + siteNavigationMenu.getSiteNavigationMenuId()
              + ") as the navigation");
    }

    var previousExternalReferenceCode = siteNavigationMenu.getExternalReferenceCode();
    siteNavigationMenu.setExternalReferenceCode(NAVIGATION_MENU_EXTERNAL_REFERENCE_CODE);
    siteNavigationMenu =
        siteNavigationMenuLocalService.updateSiteNavigationMenu(siteNavigationMenu);
    log.info(
        "Took over navigation menu "
            + siteNavigationMenu.getName()
            + " ("
            + siteNavigationMenu.getSiteNavigationMenuId()
            + "), changed its external reference code from "
            + previousExternalReferenceCode
            + " to "
            + NAVIGATION_MENU_EXTERNAL_REFERENCE_CODE);
    return siteNavigationMenu;
  }

  private static ServiceContext createServiceContext(Group group, long userId) {
    var serviceContext = new ServiceContext();
    serviceContext.setCompanyId(group.getCompanyId());
    serviceContext.setScopeGroupId(group.getGroupId());
    serviceContext.setUserId(userId);
    // The public navigation is read with the guest's permissions
    serviceContext.setAddGroupPermissions(true);
    serviceContext.setAddGuestPermissions(true);
    return serviceContext;
  }

  @Override
  public synchronized SiteNavigationMenuItem getOrCreateStudyProgramsParentMenuItem(
      AssetCategory mainCategory, AssetCategory studyProgramsCategory)
      throws MultipleStudyProgramListingMenuItemExpection, NavigationMenuItemPersistenceException {
    var group = jodOhjaajaCmsUtil.getJodOhjaajaCmsGroup();
    try {
      var userId = AdminUtil.getAdminUser(group.getCompanyId()).getUserId();
      var siteNavigationMenu = getOrCreateNavigationMenu(group, userId);

      var listingMenuItem =
          fetchMenuItem(
              STUDY_PROGRAMS_LISTING_MENU_ITEM_EXTERNAL_REFERENCE_CODE, group.getGroupId());
      if (listingMenuItem == null) {
        listingMenuItem = takeOverListingMenuItem(siteNavigationMenu);
      }
      if (listingMenuItem != null) {
        return checkListingMenuItem(listingMenuItem, siteNavigationMenu);
      }

      var serviceContext = createServiceContext(group, userId);
      var mainMenuItem =
          getOrCreateMainCategoryMenuItem(siteNavigationMenu, mainCategory, serviceContext);
      return addCategoryMenuItem(
          STUDY_PROGRAMS_LISTING_MENU_ITEM_EXTERNAL_REFERENCE_CODE,
          siteNavigationMenu,
          mainMenuItem.getSiteNavigationMenuItemId(),
          studyProgramsCategory,
          TYPE_STUDY_PROGRAMS_LISTING,
          serviceContext);
    } catch (PortalException e) {
      throw new NavigationMenuItemPersistenceException(
          "Failed to create the " + TYPE_STUDY_PROGRAMS_LISTING + " menu item", e);
    }
  }

  // A menu item created by hand is recognized by its type
  private SiteNavigationMenuItem takeOverListingMenuItem(SiteNavigationMenu siteNavigationMenu)
      throws MultipleStudyProgramListingMenuItemExpection {
    var listingMenuItems =
        getMenuItems(siteNavigationMenu).stream()
            .filter(
                menuItem -> TYPE_STUDY_PROGRAMS_LISTING.equals(getTypeCustomFieldValue(menuItem)))
            .toList();
    if (listingMenuItems.size() > 1) {
      throw new MultipleStudyProgramListingMenuItemExpection(
          "Multiple " + TYPE_STUDY_PROGRAMS_LISTING + " menu items found");
    }
    return listingMenuItems.isEmpty()
        ? null
        : takeOverMenuItem(
            listingMenuItems.getFirst(), STUDY_PROGRAMS_LISTING_MENU_ITEM_EXTERNAL_REFERENCE_CODE);
  }

  private SiteNavigationMenuItem checkListingMenuItem(
      SiteNavigationMenuItem menuItem, SiteNavigationMenu siteNavigationMenu)
      throws NavigationMenuItemPersistenceException {
    if (menuItem.getSiteNavigationMenuId() != siteNavigationMenu.getSiteNavigationMenuId()) {
      throw new NavigationMenuItemPersistenceException(
          "Menu item "
              + STUDY_PROGRAMS_LISTING_MENU_ITEM_EXTERNAL_REFERENCE_CODE
              + " ("
              + menuItem.getSiteNavigationMenuItemId()
              + ") is not in the navigation menu "
              + NAVIGATION_MENU_EXTERNAL_REFERENCE_CODE,
          null);
    }
    // An unset value reads as the default of the column, the first option of the list
    var typeValue =
        ExpandoValueLocalServiceUtil.getValue(
            menuItem.getCompanyId(),
            SiteNavigationMenuItem.class.getName(),
            ExpandoTableConstants.DEFAULT_TABLE_NAME,
            TYPE_CUSTOM_FIELD_NAME,
            menuItem.getSiteNavigationMenuItemId());
    if (typeValue == null) {
      menuItem
          .getExpandoBridge()
          .setAttribute(TYPE_CUSTOM_FIELD_NAME, new String[] {TYPE_STUDY_PROGRAMS_LISTING}, false);
      log.warn(
          "Set the missing type of menu item "
              + STUDY_PROGRAMS_LISTING_MENU_ITEM_EXTERNAL_REFERENCE_CODE
              + " to "
              + TYPE_STUDY_PROGRAMS_LISTING);
      return menuItem;
    }
    var type = getTypeCustomFieldValue(menuItem);
    if (TYPE_STUDY_PROGRAMS_LISTING.equals(type)) {
      return menuItem;
    }
    throw new NavigationMenuItemPersistenceException(
        "Menu item "
            + STUDY_PROGRAMS_LISTING_MENU_ITEM_EXTERNAL_REFERENCE_CODE
            + " ("
            + menuItem.getSiteNavigationMenuItemId()
            + ") has the type "
            + type
            + ": set its type back to "
            + TYPE_STUDY_PROGRAMS_LISTING
            + " or delete it",
        null);
  }

  private boolean isMenuItemOfCategory(
      SiteNavigationMenuItem siteNavigationMenuItem, AssetCategory assetCategory) {
    if (getSiteNavigationMenuItemType(siteNavigationMenuItem)
        != SiteNavigationMenuItemType.ASSET_CATEGORY) {
      return false;
    }
    var resolved =
        resolveAssetCategory(
            getUnicodeProperties(siteNavigationMenuItem), siteNavigationMenuItem.getGroupId());
    return resolved != null && resolved.getCategoryId() == assetCategory.getCategoryId();
  }

  private SiteNavigationMenuItem addCategoryMenuItem(
      String externalReferenceCode,
      SiteNavigationMenu siteNavigationMenu,
      long parentSiteNavigationMenuItemId,
      AssetCategory assetCategory,
      String type,
      ServiceContext serviceContext)
      throws PortalException {
    var typeSettings =
        UnicodePropertiesBuilder.create(true)
            .put("classNameId", String.valueOf(PortalUtil.getClassNameId(AssetCategory.class)))
            .put(PROPERTY_CLASS_PK, String.valueOf(assetCategory.getCategoryId()))
            .put(PROPERTY_EXTERNAL_REFERENCE_CODE, assetCategory.getExternalReferenceCode())
            .put(PROPERTY_TITLE, assetCategory.getTitle(LocaleUtil.getDefault()))
            .put(
                "type",
                ResourceActionsUtil.getModelResource(
                    LocaleUtil.getDefault(), AssetCategory.class.getName()))
            .put("useCustomName", false)
            .buildString();

    var siteNavigationMenuItem =
        siteNavigationMenuItemLocalService.addSiteNavigationMenuItem(
            externalReferenceCode,
            serviceContext.getUserId(),
            siteNavigationMenu.getGroupId(),
            siteNavigationMenu.getSiteNavigationMenuId(),
            parentSiteNavigationMenuItemId,
            AssetCategory.class.getName(),
            typeSettings,
            serviceContext);
    siteNavigationMenuItem
        .getExpandoBridge()
        .setAttribute(TYPE_CUSTOM_FIELD_NAME, new String[] {type}, false);
    log.info(
        "Created "
            + type
            + " menu item "
            + externalReferenceCode
            + " for category "
            + assetCategory.getExternalReferenceCode());
    return siteNavigationMenuItem;
  }

  @Override
  public void addOrUpdateStudyProgramNavigationMenuItem(
      JournalArticle studyProgramJournalArticle, ServiceContext serviceContext)
      throws StudyProgramListingMissingException, NavigationMenuItemPersistenceException {
    var studyProgramsParentMenuItem = getStudyProgramsParentMenuItem();
    var groupId = jodOhjaajaCmsUtil.getJodOhjaajaCmsGroup().getGroupId();
    var typeSettings =
        UnicodePropertiesBuilder.create(true)
            .put("classNameId", String.valueOf(PortalUtil.getClassNameId(JournalArticle.class)))
            .put(PROPERTY_CLASS_PK, String.valueOf(studyProgramJournalArticle.getResourcePrimKey()))
            .put("classTypeId", String.valueOf(studyProgramJournalArticle.getDDMStructureId()))
            .put(PROPERTY_TITLE, String.valueOf(studyProgramJournalArticle.getTitle()))
            .put(
                "type",
                ResourceActionsUtil.getModelResource(
                    LocaleUtil.getDefault(), JournalArticle.class.getName()))
            .put("useCustomName", false)
            .buildString();

    var existingMenuItem =
        siteNavigationMenuItemLocalService.fetchSiteNavigationMenuItemByExternalReferenceCode(
            studyProgramJournalArticle.getExternalReferenceCode(), groupId);
    if (existingMenuItem != null
        && existingMenuItem.getParentSiteNavigationMenuItemId()
            == studyProgramsParentMenuItem.getSiteNavigationMenuItemId()
        && typeSettings.equals(existingMenuItem.getTypeSettings())) {
      // Updating an unchanged menu item would only write it again on every import
      return;
    }

    try {
      siteNavigationMenuItemLocalService.addOrUpdateSiteNavigationMenuItem(
          studyProgramJournalArticle.getExternalReferenceCode(),
          serviceContext.getUserId(),
          groupId,
          studyProgramsParentMenuItem.getSiteNavigationMenuId(),
          studyProgramsParentMenuItem.getSiteNavigationMenuItemId(),
          JournalArticle.class.getName(),
          typeSettings,
          serviceContext);
    } catch (Exception e) {
      throw new NavigationMenuItemPersistenceException(
          "Failed to add or update study program navigation menu item for article "
              + studyProgramJournalArticle.getExternalReferenceCode(),
          e);
    }
  }

  @Override
  public void deleteStudyProgramNavigationMenuItem(String externalReferenceCode)
      throws PortalException {

    siteNavigationMenuItemLocalService.deleteSiteNavigationMenuItem(
        externalReferenceCode, jodOhjaajaCmsUtil.getJodOhjaajaCmsGroup().getGroupId());
  }

  public SiteNavigationMenuItem getStudyProgramsParentMenuItem()
      throws StudyProgramListingMissingException {
    var menuItem =
        fetchMenuItem(
            STUDY_PROGRAMS_LISTING_MENU_ITEM_EXTERNAL_REFERENCE_CODE,
            jodOhjaajaCmsUtil.getJodOhjaajaCmsGroup().getGroupId());
    if (menuItem == null) {
      throw new StudyProgramListingMissingException(
          "No menu item "
              + STUDY_PROGRAMS_LISTING_MENU_ITEM_EXTERNAL_REFERENCE_CODE
              + ", it is created or taken over by the import");
    }
    return menuItem;
  }

  private String getTypeCustomFieldValue(SiteNavigationMenuItem siteNavigationMenuItem) {
    var expandoBridge = siteNavigationMenuItem.getExpandoBridge();
    var attributes = expandoBridge.getAttributes(false);
    if (attributes.containsKey(TYPE_CUSTOM_FIELD_NAME)
        && attributes.get(TYPE_CUSTOM_FIELD_NAME) instanceof String[] customFieldData
        && customFieldData.length > 0) {
      return customFieldData[0];
    }
    return null;
  }

  private NavigationDto toNavigationDto(SiteNavigationMenu siteNavigationMenu, String languageId) {
    var siteNavigationMenuItemsMap =
        getSiteNavigationMenuItemsMap(
            siteNavigationMenuItemLocalService.getSiteNavigationMenuItems(
                siteNavigationMenu.getSiteNavigationMenuId(),
                SiteNavigationMenuItemOrderComparator.getInstance(true)));

    return new NavigationDto(
        siteNavigationMenu.getSiteNavigationMenuId(),
        siteNavigationMenu.getGroupId(),
        siteNavigationMenuItemsMap.getOrDefault(0L, Collections.emptyList()).stream()
            .map(
                siteNavigationMenuItem ->
                    toNavigationItemDto(
                        siteNavigationMenuItem, languageId, siteNavigationMenuItemsMap))
            .collect(Collectors.toList()));
  }

  private Map<Long, List<SiteNavigationMenuItem>> getSiteNavigationMenuItemsMap(
      List<SiteNavigationMenuItem> siteNavigationMenuItems) {

    return siteNavigationMenuItems.stream()
        .collect(Collectors.groupingBy(SiteNavigationMenuItem::getParentSiteNavigationMenuItemId));
  }

  private NavigationItemDto toNavigationItemDto(
      SiteNavigationMenuItem siteNavigationMenuItem,
      String languageId,
      Map<Long, List<SiteNavigationMenuItem>> siteNavigationMenuItemsMap) {

    var type = getSiteNavigationMenuItemType(siteNavigationMenuItem);
    var unicodeProperties = getUnicodeProperties(siteNavigationMenuItem);
    var groupId = siteNavigationMenuItem.getGroupId();
    var assetCategory = getAssetCategory(type, unicodeProperties, groupId);
    var journalArticle = getJournalArticle(type, unicodeProperties, groupId);
    var name = getName(assetCategory, journalArticle, type, unicodeProperties, languageId);
    var nameI18n = getNameI18n(assetCategory, journalArticle, type);
    var description = getDescription(assetCategory, journalArticle, type, languageId);
    var descriptionI18n = getDescriptionI18n(assetCategory, journalArticle, type);
    var externalReferenceCode =
        resolveExternalReferenceCode(siteNavigationMenuItem, assetCategory, journalArticle);

    return new NavigationItemDto(
        siteNavigationMenuItem.getSiteNavigationMenuItemId(),
        name,
        nameI18n,
        SlugUtil.sluggify(name),
        SlugUtil.sluggifyAll(nameI18n),
        description,
        descriptionI18n,
        getType(siteNavigationMenuItem, type),
        getBooleanCustomFieldValue(
            siteNavigationMenuItem, HIDE_FROM_HOME_PAGE_NEWEST_CAROUSEL_FIELD_NAME),
        getBooleanCustomFieldValue(
            siteNavigationMenuItem, HIDE_FROM_HOME_PAGE_MOST_VIEWED_CAROUSEL_FIELD_NAME),
        getBooleanCustomFieldValue(
            siteNavigationMenuItem, HIDE_FROM_MAIN_CATEGORY_PAGE_NEWEST_CAROUSEL_FIELD_NAME),
        getBooleanCustomFieldValue(
            siteNavigationMenuItem, HIDE_FROM_MAIN_CATEGORY_PAGE_MOST_VIEWED_CAROUSEL_FIELD_NAME),
        externalReferenceCode,
        (journalArticle != null) ? journalArticle.getResourcePrimKey() : null,
        (assetCategory != null) ? assetCategory.getCategoryId() : null,
        siteNavigationMenuItemsMap
            .getOrDefault(
                siteNavigationMenuItem.getSiteNavigationMenuItemId(), Collections.emptyList())
            .stream()
            .map(
                childSiteNavigationMenuItem ->
                    toNavigationItemDto(
                        childSiteNavigationMenuItem, languageId, siteNavigationMenuItemsMap))
            .collect(Collectors.toList()),
        siteNavigationMenuItem.getParentSiteNavigationMenuItemId());
  }

  private String resolveExternalReferenceCode(
      SiteNavigationMenuItem siteNavigationMenuItem,
      AssetCategory assetCategory,
      JournalArticle journalArticle) {
    if (assetCategory != null) {
      return assetCategory.getExternalReferenceCode();
    }
    if (journalArticle != null) {
      return journalArticle.getExternalReferenceCode();
    }
    return siteNavigationMenuItem.getExternalReferenceCode();
  }

  private SiteNavigationMenuItemType getSiteNavigationMenuItemType(
      SiteNavigationMenuItem siteNavigationMenuItem) {
    return switch (siteNavigationMenuItem.getType()) {
      case ASSET_CATEGORY_CLASS_NAME -> SiteNavigationMenuItemType.ASSET_CATEGORY;
      case JOURNAL_ARTICLE_CLASS_NAME -> SiteNavigationMenuItemType.JOURNAL_ARTICLE;
      default -> SiteNavigationMenuItemType.NOT_RELEVANT;
    };
  }

  private UnicodeProperties getUnicodeProperties(SiteNavigationMenuItem siteNavigationMenuItem) {
    if (siteNavigationMenuItem == null) {
      return new UnicodeProperties();
    }
    return UnicodePropertiesBuilder.fastLoad(siteNavigationMenuItem.getTypeSettings()).build();
  }

  // Menu items reference their target either by classPK or, when created in the DXP 2026 UI, by
  // externalReferenceCode. If the target cannot be resolved or the user may not view it, the item
  // is still returned, without an ID and with the title stored in the menu item's typeSettings.
  private AssetCategory getAssetCategory(
      SiteNavigationMenuItemType type, UnicodeProperties unicodeProperties, long groupId) {
    if (type != SiteNavigationMenuItemType.ASSET_CATEGORY) {
      return null;
    }
    var assetCategory = resolveAssetCategory(unicodeProperties, groupId);
    return PermissionUtil.canView(assetCategoryModelResourcePermission, assetCategory)
        ? assetCategory
        : null;
  }

  /**
   * The category a menu item refers to, by classPK or, if created in the DXP 2026 UI, by external
   * reference code, without checking the permissions.
   */
  private AssetCategory resolveAssetCategory(UnicodeProperties unicodeProperties, long groupId) {
    var categoryId = GetterUtil.getLong(unicodeProperties.get(PROPERTY_CLASS_PK));
    if (categoryId > 0) {
      return assetCategoryLocalService.fetchAssetCategory(categoryId);
    }
    var externalReferenceCode = unicodeProperties.get(PROPERTY_EXTERNAL_REFERENCE_CODE);
    if (Validator.isNull(externalReferenceCode)) {
      return null;
    }
    return assetCategoryLocalService.fetchAssetCategoryByExternalReferenceCode(
        externalReferenceCode, groupId);
  }

  // Only the latest approved version is shown: a newer draft must not hide a published article
  private JournalArticle getJournalArticle(
      SiteNavigationMenuItemType type, UnicodeProperties unicodeProperties, long groupId) {
    if (type != SiteNavigationMenuItemType.JOURNAL_ARTICLE) {
      return null;
    }
    var resourcePrimKey = GetterUtil.getLong(unicodeProperties.get(PROPERTY_CLASS_PK));
    JournalArticle journalArticle;
    if (resourcePrimKey > 0) {
      journalArticle =
          journalArticleLocalService.fetchLatestArticle(
              resourcePrimKey, WorkflowConstants.STATUS_APPROVED);
    } else {
      var externalReferenceCode = unicodeProperties.get(PROPERTY_EXTERNAL_REFERENCE_CODE);
      if (Validator.isNull(externalReferenceCode)) {
        return null;
      }
      journalArticle =
          journalArticleLocalService.fetchLatestArticleByExternalReferenceCode(
              groupId, externalReferenceCode, WorkflowConstants.STATUS_APPROVED, true);
    }
    return PermissionUtil.canView(journalArticleModelResourcePermission, journalArticle)
        ? journalArticle
        : null;
  }

  private String getName(
      AssetCategory category,
      JournalArticle article,
      SiteNavigationMenuItemType type,
      UnicodeProperties unicodeProperties,
      String languageId) {

    return switch (type) {
      case ASSET_CATEGORY ->
          (category != null)
              ? category.getTitle(languageId)
              : unicodeProperties.getProperty(PROPERTY_TITLE);
      case JOURNAL_ARTICLE ->
          (article != null)
              ? article.getTitle(languageId)
              : unicodeProperties.getProperty(PROPERTY_TITLE);
      default -> unicodeProperties.getProperty(PROPERTY_TITLE);
    };
  }

  private Map<String, String> getNameI18n(
      AssetCategory category, JournalArticle article, SiteNavigationMenuItemType type) {

    return switch (type) {
      case ASSET_CATEGORY ->
          (category != null) ? LocalizedMapUtil.getI18nMap(category.getTitleMap()) : Map.of();
      case JOURNAL_ARTICLE ->
          (article != null) ? LocalizedMapUtil.getI18nMap(article.getTitleMap()) : Map.of();
      default -> Map.of();
    };
  }

  private String getDescription(
      AssetCategory category,
      JournalArticle article,
      SiteNavigationMenuItemType type,
      String languageId) {

    return switch (type) {
      case ASSET_CATEGORY -> (category != null) ? category.getDescription(languageId) : "";
      case JOURNAL_ARTICLE -> (article != null) ? article.getDescription(languageId) : "";
      default -> "";
    };
  }

  private Map<String, String> getDescriptionI18n(
      AssetCategory category, JournalArticle article, SiteNavigationMenuItemType type) {

    return switch (type) {
      case ASSET_CATEGORY ->
          (category != null) ? LocalizedMapUtil.getI18nMap(category.getDescriptionMap()) : Map.of();
      case JOURNAL_ARTICLE ->
          (article != null) ? LocalizedMapUtil.getI18nMap(article.getDescriptionMap()) : Map.of();
      default -> Map.of();
    };
  }

  private boolean getBooleanCustomFieldValue(
      SiteNavigationMenuItem siteNavigationMenuItem, String fieldName) {
    var expandoBridge = siteNavigationMenuItem.getExpandoBridge();
    var attributes = expandoBridge.getAttributes(false);
    if (attributes.containsKey(fieldName) && attributes.get(fieldName) instanceof Boolean value) {
      return value;
    }
    return false;
  }

  private String getType(
      SiteNavigationMenuItem siteNavigationMenuItem,
      SiteNavigationMenuItemType siteNavigationMenuItemType) {
    return switch (siteNavigationMenuItemType) {
      case ASSET_CATEGORY -> {
        var expandoBridge = siteNavigationMenuItem.getExpandoBridge();
        var attributes = expandoBridge.getAttributes(false);

        yield attributes.containsKey(TYPE_CUSTOM_FIELD_NAME)
                && attributes.get(TYPE_CUSTOM_FIELD_NAME) instanceof String[] customFieldData
                && customFieldData.length > 0
            ? customFieldData[0]
            : "CategoryListing";
      }
      case JOURNAL_ARTICLE -> "Article";
      default -> siteNavigationMenuItem.getType();
    };
  }

  private void initCustomFields() {
    var companyId = PortalUtil.getDefaultCompanyId();
    try {
      AdminUtil.runAsAdmin(
          companyId,
          () -> {
            var userId = PrincipalThreadLocal.getUserId();
            var expandoBridge =
                ExpandoBridgeFactoryUtil.getExpandoBridge(
                    companyId, SiteNavigationMenuItem.class.getName());

            for (var customFieldData : CUSTOM_FIELDS) {
              initCustomField(
                  expandoBridge,
                  customFieldData.name(),
                  customFieldData.type(),
                  customFieldData.defaultValue(),
                  customFieldData.propertyDisplayType());
              grantUserPermissionsForCustomField(customFieldData.name());

              customFieldData.localizationMap.forEach(
                  (locale, name) -> {
                    try {
                      ploEntryLocalService.addOrUpdatePLOEntry(
                          companyId, userId, customFieldData.name(), locale, name);
                    } catch (PortalException e) {
                      log.error(
                          "Failed to add or update PLO entry for custom field "
                              + customFieldData.name()
                              + " and locale "
                              + locale,
                          e);
                    }
                  });
            }
          });
    } catch (PortalException e) {
      throw new NavigationInitializationException(
          "Failed to initialize navigation custom fields", e);
    }
  }

  private void initCustomField(
      ExpandoBridge expandoBridge,
      String customFieldName,
      int customFieldType,
      Serializable customFieldDefaultData,
      String propertyDisplayType)
      throws PortalException {
    if (expandoBridge.getAttributes().containsKey(customFieldName)) {
      log.info("Custom field " + customFieldName + " already exists");
      expandoBridge.setAttributeDefault(customFieldName, customFieldDefaultData);
    } else {
      expandoBridge.addAttribute(customFieldName, customFieldType, customFieldDefaultData, false);
      var unicodeProperties = getUnicodeProperties(propertyDisplayType);
      expandoBridge.setAttributeProperties(customFieldName, unicodeProperties, false);
      log.info("Added custom field " + customFieldName);
    }
  }

  private void grantUserPermissionsForCustomField(String customFieldName) throws PortalException {
    var companyId = PortalUtil.getDefaultCompanyId();
    var userRole = RoleLocalServiceUtil.fetchRole(companyId, RoleConstants.USER);

    if (userRole == null) {
      log.warn("User role not found, cannot grant custom field permissions for " + customFieldName);
      return;
    }

    var expandoColumn =
        ExpandoColumnLocalServiceUtil.getColumn(
            companyId,
            SiteNavigationMenuItem.class.getName(),
            ExpandoTableConstants.DEFAULT_TABLE_NAME,
            customFieldName);

    ResourcePermissionLocalServiceUtil.setResourcePermissions(
        companyId,
        ExpandoColumn.class.getName(),
        ResourceConstants.SCOPE_INDIVIDUAL,
        String.valueOf(expandoColumn.getColumnId()),
        userRole.getRoleId(),
        new String[] {ActionKeys.VIEW, ActionKeys.UPDATE});
  }

  private static UnicodeProperties getUnicodeProperties(String propertyDisplayType) {
    var unicodeProperties = new UnicodeProperties();
    unicodeProperties.setProperty(ExpandoColumnConstants.PROPERTY_HIDDEN, "false");
    unicodeProperties.setProperty(
        ExpandoColumnConstants.PROPERTY_DISPLAY_TYPE, propertyDisplayType);
    unicodeProperties.setProperty(
        ExpandoColumnConstants.INDEX_TYPE,
        String.valueOf(ExpandoColumnConstants.INDEX_TYPE_KEYWORD));
    unicodeProperties.setProperty(ExpandoColumnConstants.PROPERTY_LOCALIZE_FIELD_NAME, "true");
    unicodeProperties.setProperty(
        ExpandoColumnConstants.PROPERTY_VISIBLE_WITH_UPDATE_PERMISSION, "false");
    return unicodeProperties;
  }

  private enum SiteNavigationMenuItemType {
    ASSET_CATEGORY,
    JOURNAL_ARTICLE,
    NOT_RELEVANT
  }

  private record CustomFieldData(
      String name,
      int type,
      Serializable defaultValue,
      String propertyDisplayType,
      Map<String, String> localizationMap) {}
}
