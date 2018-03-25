/*
 * Copyright (c) 2016.
 * Modified on 02/09/2016.
 */

package cm.aptoide.pt;

import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.Intent;
import android.content.UriMatcher;
import android.content.pm.ShortcutManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Base64;
import cm.aptoide.pt.ads.MinimalAdMapper;
import cm.aptoide.pt.analytics.NavigationTracker;
import cm.aptoide.pt.analytics.analytics.AnalyticsManager;
import cm.aptoide.pt.crashreports.CrashReport;
import cm.aptoide.pt.dataprovider.WebService;
import cm.aptoide.pt.dataprovider.model.v2.GetAdsResponse;
import cm.aptoide.pt.dataprovider.ws.v7.GetAppRequest;
import cm.aptoide.pt.install.InstalledRepository;
import cm.aptoide.pt.link.AptoideInstall;
import cm.aptoide.pt.link.AptoideInstallParser;
import cm.aptoide.pt.logger.Logger;
import cm.aptoide.pt.repository.RepositoryFactory;
import cm.aptoide.pt.store.StoreUtils;
import cm.aptoide.pt.utils.AptoideUtils;
import cm.aptoide.pt.utils.design.ShowMessage;
import cm.aptoide.pt.view.ActivityView;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

public class DeepLinkIntentReceiver extends ActivityView {

  public static final String AUTHORITY = "cm.aptoide.pt";
  public static final int DEEPLINK_ID = 1;
  public static final int SCHEDULE_DOWNLOADS_ID = 2;
  public static final String DEEP_LINK = "deeplink";
  public static final String SCHEDULE_DOWNLOADS = "schedule_downloads";
  public static final String FROM_SHORTCUT = "from_shortcut";
  private static final String TAG = DeepLinkIntentReceiver.class.getSimpleName();
  private static final UriMatcher sURIMatcher = new UriMatcher(UriMatcher.NO_MATCH);

  static {
    sURIMatcher.addURI(AUTHORITY, DEEP_LINK, DEEPLINK_ID);
    sURIMatcher.addURI(AUTHORITY, SCHEDULE_DOWNLOADS, SCHEDULE_DOWNLOADS_ID);
  }

  private ArrayList<String> server;
  private HashMap<String, String> app;
  private String TMP_MYAPP_FILE;
  private Class startClass = AptoideApplication.getActivityProvider()
      .getMainActivityFragmentClass();
  private AsyncTask<String, Void, Void> asyncTask;
  private InstalledRepository installedRepository;
  private MinimalAdMapper adMapper;
  private AnalyticsManager analyticsManager;
  private NavigationTracker navigationTracker;
  private DeepLinkAnalytics deepLinkAnalytics;
  private boolean shortcutNavigation;

  @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    final AptoideApplication application = (AptoideApplication) getApplicationContext();
    analyticsManager = application.getAnalyticsManager();
    navigationTracker = application.getNavigationTracker();
    deepLinkAnalytics = new DeepLinkAnalytics(analyticsManager, navigationTracker);
    installedRepository = RepositoryFactory.getInstalledRepository(getApplicationContext());

    adMapper = new MinimalAdMapper();
    TMP_MYAPP_FILE = getCacheDir() + "/myapp.myapp";
    String uri = getIntent().getDataString();
    deepLinkAnalytics.website(uri);
    shortcutNavigation = false;

    Logger.v(TAG, "uri: " + uri);

    dealWithShortcuts();

    Uri u = null;
    try {
      u = Uri.parse(uri);
    } catch (Exception e) {
      CrashReport.getInstance()
          .log(e);
    }
    //Loogin for url from the new site
    if (u != null && u.getHost() != null) {

      if (u.getHost()
          .contains("webservices.aptoide.com")) {
        dealWithWebservicesAptoide(uri);
      } else if (u.getHost()
          .contains("imgs.aptoide.com")) {
        dealWithImagesApoide(uri);
      } else if (u.getHost()
          .contains("aptoide.com")) {
        dealWithAptoideWebsite(u);
      } else if ("aptoiderepo".equalsIgnoreCase(u.getScheme())) {
        dealWithAptoideRepo(uri);
      } else if ("aptoidexml".equalsIgnoreCase(u.getScheme())) {
        dealWithAptoideXml(uri);
      } else if ("aptoidesearch".equalsIgnoreCase(u.getScheme())) {
        startFromPackageName(uri.split("aptoidesearch://")[1]);
      } else if ("market".equalsIgnoreCase(u.getScheme())) {
        dealWithMArketSchema(uri, u);
      } else if (u.getHost()
          .contains("market.android.com")) {
        startFromPackageName(u.getQueryParameter("id"));
      } else if (u.getHost()
          .contains("play.google.com") && u.getPath()
          .equalsIgnoreCase("store/apps/details")) {
        dealWithGoogleHost(u);
      } else if ("aptword".equalsIgnoreCase(u.getScheme())) {
        dealWithAptword(uri);
      } else if ("file".equalsIgnoreCase(u.getScheme())) {
        downloadMyApp();
      } else if ("aptoideinstall".equalsIgnoreCase(u.getScheme())) {
        parseAptoideInstallUri(uri.substring("aptoideinstall://".length()));
      } else if (u.getHost()
          .equals("cm.aptoide.pt") && u.getPath()
          .equals("/deeplink") && "aptoide".equalsIgnoreCase(u.getScheme())) {
        dealWithAptoideSchema(u);
      }
    }
    deepLinkAnalytics.sendWebsite();
    finish();
  }

  private void dealWithAptoideSchema(Uri u) {
    if ("getHome".equals(u.getQueryParameter("name"))) {
      String id = u.getQueryParameter("user_id");
      if (id != null) {
        openUserScreen(Long.valueOf(id));
      }
    } else if ("getUserTimeline".equals(u.getQueryParameter("name"))) {
      startFromAppsTimeline(u.getQueryParameter("cardId"));
    } else if ("search".equals(u.getQueryParameter("name"))) {
      String query = "";
      if (u.getQueryParameterNames()
          .contains("keyword")) {
        query = u.getQueryParameter("keyword");
      }
      startFromSearch(query);
    } else if ("myStore".equals(u.getQueryParameter("name"))) {
      startFromMyStore();
    } else if ("pickApp".equals(u.getQueryParameter("name"))) {
      startFromPickApp();
    } else if (sURIMatcher.match(u) == DEEPLINK_ID) {
      startGenericDeepLink(u);
    }
  }

  private void dealWithAptword(String uri) {
    // TODO: 12-08-2016 neuro aptword Seems discontinued???
    String param = uri.substring("aptword://".length());

    if (!TextUtils.isEmpty(param)) {

      param = param.replaceAll("\\*", "_")
          .replaceAll("\\+", "/");

      String json = new String(Base64.decode(param.getBytes(), 0));

      Logger.d("AptoideAptWord", json);

      GetAdsResponse.Ad ad = null;
      try {
        ad = new ObjectMapper().readValue(json, GetAdsResponse.Ad.class);
      } catch (IOException e) {
        CrashReport.getInstance()
            .log(e);
      }

      if (ad != null) {
        Intent i = new Intent(this, startClass);
        i.putExtra(DeepLinksTargets.FROM_AD, adMapper.map(ad));
        startActivity(i);
      }
    }
  }

  private void dealWithGoogleHost(Uri uri) {
    String param = uri.getQueryParameter("id");
    if (param.contains("pname:")) {
      param = param.substring(6);
    } else if (param.contains("pub:")) {
      param = param.substring(4);
    }
    startFromPackageName(param);
  }

  private void dealWithMArketSchema(String uri, Uri u) {
  /*
   * market schema:
   * could come from a search or a to open an app
   */
    String packageName = "";
    if ("details".equalsIgnoreCase(u.getHost())) {
      packageName = u.getQueryParameter("id");
    } else if ("search".equalsIgnoreCase(u.getHost())) {
      packageName = u.getQueryParameter("q");
    } else {
      //old code
      String params = uri.split("&")[0];
      String[] param = params.split("=");
      packageName = (param != null && param.length > 1) ? params.split("=")[1] : "";
      if (packageName.contains("pname:")) {
        packageName = packageName.substring(6);
      } else if (packageName.contains("pub:")) {
        packageName = packageName.substring(4);
      }
    }
    startFromPackageName(packageName);
  }

  private void dealWithAptoideXml(String uri) {
    String repo = uri.substring(13);
    parseXmlString(repo);
    Intent i = new Intent(DeepLinkIntentReceiver.this, startClass);
    i.putExtra(DeepLinksTargets.NEW_REPO, StoreUtils.split(repo));
    startActivity(i);
  }

  private void dealWithAptoideRepo(String uri) {
    ArrayList<String> repo = new ArrayList<>();
    repo.add(uri.substring(14));
    startWithRepo(StoreUtils.split(repo));
  }

  private void dealWithAptoideWebsite(Uri u) {
    /**
     * Coming from our web site.
     * This could be from and to:
     * a store
     * or a app view
     * or home (tab/website)
     * or bundle with format store/apps/group
     */
    if (u.getPath() != null && u.getPath()
        .contains("store/apps/group")) {
      /**
       *
       */
      deepLinkAnalytics.websiteFromBundlesWebPage();
      String bundleId = u.getLastPathSegment();
      Logger.v(TAG, "aptoide web site: bundle: " + bundleId);
      if (!TextUtils.isEmpty(bundleId)
          && bundleId.contains("-")
          && bundleId.indexOf("-") < bundleId.length()) {
        startFromBundle(bundleId.substring(bundleId.indexOf("-") + 1));
      }
    } else if (u.getPath() != null && u.getPath()
        .contains("store")) {

      /**
       * store
       */
      deepLinkAnalytics.websiteFromStoreWebPage();
      Logger.v(TAG, "aptoide web site: store: " + u.getLastPathSegment());
      ArrayList<String> list = new ArrayList<String>();
      list.add(u.getLastPathSegment());
      startWithRepo(list);
    } else {
      String[] appName = u.getHost()
          .split("\\.");
      if (appName != null && appName.length == 4) {

        /**
         * App view
         */
        deepLinkAnalytics.websiteFromAppViewWebPage();
        Logger.v(TAG, "aptoide web site: app view: " + appName[0]);
        startAppView(appName[0]);
      } else if (appName != null && appName.length == 3) {
        /**
         * Home
         */
        deepLinkAnalytics.websiteFromHomeWebPage();
        Logger.v(TAG, "aptoide web site: home: " + appName[0]);
        startFromHome();
      }
    }
  }

  private void dealWithImagesApoide(String uri) {
    String[] strings = uri.split("-");
    long id = Long.parseLong(strings[strings.length - 1].split("\\.myapp")[0]);

    startFromAppView(id, null, false);
  }

  private void dealWithWebservicesAptoide(String uri) {
    /** refactored to remove org.apache libs */
    Map<String, String> params = null;

    try {
      params = AptoideUtils.StringU.splitQuery(URI.create(uri));
    } catch (UnsupportedEncodingException e) {
      CrashReport.getInstance()
          .log(e);
    }

    if (params != null) {
      String uid = null;
      for (Map.Entry<String, String> entry : params.entrySet()) {
        if (entry.getKey()
            .equals("uid")) {
          uid = entry.getValue();
        }
      }

      if (uid != null) {
        try {
          long id = Long.parseLong(uid);
          startFromAppView(id, null, true);
        } catch (NumberFormatException e) {
          CrashReport.getInstance()
              .log(e);
          CrashReport.getInstance()
              .log(e);
          ShowMessage.asToast(getApplicationContext(), R.string.simple_error_occured + uid);
        }
      }
    }
  }

  private void dealWithShortcuts() {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N_MR1) {

      ShortcutManager shortcutManager =
          getApplicationContext().getSystemService(ShortcutManager.class);
      Intent fromShortcut = getIntent();

      if (fromShortcut != null) {
        if (fromShortcut.hasExtra("search")) {
          if (fromShortcut.getBooleanExtra("search", false)) {
            shortcutNavigation = true;
            if (shortcutManager != null) {
              shortcutManager.reportShortcutUsed("search");
            }
          }
        } else if (fromShortcut.hasExtra("timeline")) {
          if (fromShortcut.getBooleanExtra("timeline", false)) {
            shortcutNavigation = true;
            if (shortcutManager != null) {
              shortcutManager.reportShortcutUsed("timeline");
            }
          }
        }
      }
    }
  }

  private void openUserScreen(Long userId) {
    Intent i = new Intent(DeepLinkIntentReceiver.this, startClass);
    i.putExtra(DeepLinksTargets.USER_DEEPLINK, userId);
    startActivity(i);
  }

  public void startWithRepo(ArrayList<String> repo) {
    Intent i = new Intent(DeepLinkIntentReceiver.this, startClass);
    i.putExtra(DeepLinksTargets.NEW_REPO, repo);
    startActivity(i);

    // TODO: 10-08-2016 jdandrade
    deepLinkAnalytics.newRepo();
  }

  private void parseXmlString(String file) {

    try {
      SAXParserFactory spf = SAXParserFactory.newInstance();
      SAXParser sp = spf.newSAXParser();
      XMLReader xr = sp.getXMLReader();
      XmlAppHandler handler = new XmlAppHandler();
      xr.setContentHandler(handler);

      InputSource is = new InputSource();
      is.setCharacterStream(new StringReader(file));
      xr.parse(is);
      server = handler.getServers();
      app = handler.getApp();
    } catch (IOException | SAXException | ParserConfigurationException e) {
      CrashReport.getInstance()
          .log(e);
    }
  }

  @Override public void startActivity(Intent intent) {
    super.startActivity(intent);
    finish();
  }

  public void startFromPackageName(String packageName) {
    if (packageName != null) {
      GetAppRequest.of(packageName,
          ((AptoideApplication) getApplicationContext()).getAccountSettingsBodyInterceptorPoolV7(),
          ((AptoideApplication) getApplicationContext()).getDefaultClient(),
          WebService.getDefaultConverter(),
          ((AptoideApplication) getApplicationContext()).getTokenInvalidator(),
          ((AptoideApplication) getApplicationContext()).getDefaultSharedPreferences())
          .observe()
          .subscribe(app -> {
            if (app.isOk()) {
              startFromAppView(packageName);
            } else {
              startFromSearch(packageName);
            }
          }, err -> {
            startFromSearch(packageName);
          });
    }
  }

  public void startAppView(String uname) {
    Intent i = new Intent(this, startClass);

    i.putExtra(DeepLinksTargets.APP_VIEW_FRAGMENT, true);
    i.putExtra(DeepLinksKeys.UNAME, uname);

    //TODO this is not in MainActivity
    startActivity(i);
  }

  public void startFromAppView(long id, String packageName, boolean showPopup) {
    Intent i = new Intent(this, startClass);

    i.putExtra(DeepLinksTargets.APP_VIEW_FRAGMENT, true);
    i.putExtra(DeepLinksKeys.APP_ID_KEY, id);
    i.putExtra(DeepLinksKeys.PACKAGE_NAME_KEY, packageName);
    i.putExtra(DeepLinksKeys.SHOW_AUTO_INSTALL_POPUP, showPopup);

    startActivity(i);
  }

  public void startFromAppsTimeline(String cardId) {
    Intent i = new Intent(this, startClass);
    i.putExtra(DeepLinksTargets.TIMELINE_DEEPLINK, true);
    i.putExtra(DeepLinksKeys.CARD_ID, cardId);
    if (shortcutNavigation) i.putExtra(FROM_SHORTCUT, shortcutNavigation);

    startActivity(i);
  }

  public void startFromBundle(String bundleId) {
    Intent i = new Intent(this, startClass);
    i.putExtra(DeepLinksTargets.BUNDLE, true);
    i.putExtra(DeepLinksKeys.BUNDLE_ID, bundleId);
    startActivity(i);
  }

  public void startFromHome() {
    Intent i = new Intent(this, startClass);
    i.putExtra(DeepLinksTargets.HOME_DEEPLINK, true);
    startActivity(i);
  }

  private void downloadMyApp() {
    asyncTask = new MyAppDownloader().execute(getIntent().getDataString());
  }

  private void parseAptoideInstallUri(String substring) {
    AptoideInstallParser parser = new AptoideInstallParser();
    AptoideInstall aptoideInstall = parser.parse(substring);
    if (aptoideInstall.getAppId() > 0) {
      startFromAppView(aptoideInstall.getAppId(), aptoideInstall.getPackageName(), false);
    } else {
      startFromAppview(aptoideInstall.getStoreName(), aptoideInstall.getPackageName(),
          aptoideInstall.shouldShowPopup());
    }
  }

  private void startGenericDeepLink(Uri parse) {
    Intent intent = new Intent(this, startClass);
    intent.putExtra(DeepLinksTargets.GENERIC_DEEPLINK, true);
    intent.putExtra(DeepLinksKeys.URI, parse);
    startActivity(intent);
  }

  public void startFromAppView(String packageName) {
    Intent i = new Intent(this, startClass);

    i.putExtra(DeepLinksTargets.APP_VIEW_FRAGMENT, true);
    i.putExtra(DeepLinksKeys.PACKAGE_NAME_KEY, packageName);

    startActivity(i);
  }

  public void startFromSearch(String query) {
    Intent i = new Intent(this, startClass);

    i.putExtra(DeepLinksTargets.SEARCH_FRAGMENT, true);
    i.putExtra(SearchManager.QUERY, query);
    i.putExtra(FROM_SHORTCUT, shortcutNavigation);

    startActivity(i);
  }

  private void startFromAppview(String repo, String packageName, boolean showPopup) {
    Intent intent = new Intent(this, startClass);
    intent.putExtra(DeepLinksTargets.APP_VIEW_FRAGMENT, true);
    intent.putExtra(DeepLinksKeys.PACKAGE_NAME_KEY, packageName);
    intent.putExtra(DeepLinksKeys.STORENAME_KEY, repo);
    intent.putExtra(DeepLinksKeys.SHOW_AUTO_INSTALL_POPUP, showPopup);
    startActivity(intent);
  }

  private void startFromMyStore() {
    Intent intent = new Intent(this, startClass);
    intent.putExtra(DeepLinksTargets.MY_STORE_DEEPLINK, true);
    startActivity(intent);
  }

  private void startFromPickApp() {
    Intent intent = new Intent(this, startClass);
    intent.putExtra(DeepLinksTargets.PICK_APP_DEEPLINK, true);
    startActivity(intent);
  }

  private void downloadMyAppFile(String myappUri) throws Exception {
    try {
      URL url = new URL(myappUri);
      URLConnection connection;
      if (!myappUri.startsWith("file://")) {
        connection = url.openConnection();
        connection.setReadTimeout(5000);
        connection.setConnectTimeout(5000);
      } else {
        connection = url.openConnection();
      }

      BufferedInputStream getit = new BufferedInputStream(connection.getInputStream(), 1024);

      File file_teste = new File(TMP_MYAPP_FILE);
      if (file_teste.exists()) {
        file_teste.delete();
      }

      FileOutputStream saveit = new FileOutputStream(TMP_MYAPP_FILE);
      BufferedOutputStream bout = new BufferedOutputStream(saveit, 1024);
      byte data[] = new byte[1024];

      int readed = getit.read(data, 0, 1024);
      while (readed != -1) {
        bout.write(data, 0, readed);
        readed = getit.read(data, 0, 1024);
      }

      bout.close();
      getit.close();
      saveit.close();
    } catch (Exception e) {
      CrashReport.getInstance()
          .log(e);
    }
  }

  private void parseXmlMyapp(String file) throws Exception {

    try {
      SAXParserFactory spf = SAXParserFactory.newInstance();
      SAXParser sp = spf.newSAXParser();
      XmlAppHandler handler = new XmlAppHandler();
      sp.parse(new File(file), handler);
      server = handler.getServers();
      app = handler.getApp();
    } catch (IOException | SAXException | ParserConfigurationException e) {
      CrashReport.getInstance()
          .log(e);
    }
  }

  private void proceed() {
    if (server != null) {
      startWithRepo(StoreUtils.split(server));
    } else {
      ShowMessage.asToast(this, getString(R.string.error_occured));
      finish();
    }
  }

  public static class DeepLinksTargets {

    public static final String NEW_REPO = "newrepo";
    public static final String FROM_DOWNLOAD_NOTIFICATION = "fromDownloadNotification";
    public static final String NEW_UPDATES = "new_updates";
    public static final String FROM_AD = "fromAd";
    public static final String APP_VIEW_FRAGMENT = "appViewFragment";
    public static final String SEARCH_FRAGMENT = "searchFragment";
    public static final String GENERIC_DEEPLINK = "generic_deeplink";
    public static final String USER_DEEPLINK = "open_user_profile";
    public static final String TIMELINE_DEEPLINK = "apps_timeline";
    public static final String HOME_DEEPLINK = "home_tab";
    public static final String MY_STORE_DEEPLINK = "my_store";
    public static final String PICK_APP_DEEPLINK = "pick_app_deeplink";
    public static final String BUNDLE = "bundle";
  }

  public static class DeepLinksKeys {

    public static final String APP_MD5_KEY = "md5";
    public static final String APP_ID_KEY = "appId";
    public static final String PACKAGE_NAME_KEY = "packageName";
    public static final String UNAME = "uname";
    public static final String STORENAME_KEY = "storeName";
    public static final String SHOW_AUTO_INSTALL_POPUP = "show_auto_install_popup";
    public static final String URI = "uri";
    public static final String CARD_ID = "cardId";
    public static final String OPEN_MODE = "openMode";
    public static final String BUNDLE_ID = "bundle_id";

    //deep link query parameters
    public static final String ACTION = "action";
    public static final String TYPE = "type";
    public static final String NAME = "name";
    public static final String LAYOUT = "layout";
    public static final String TITLE = "title";
    public static final String STORE_THEME = "storetheme";
    public static final String SHOULD_INSTALL = "SHOULD_INSTALL";
  }

  class MyAppDownloader extends AsyncTask<String, Void, Void> {

    ProgressDialog pd;

    @Override protected Void doInBackground(String... params) {

      try {
        downloadMyAppFile(params[0]);
        parseXmlMyapp(TMP_MYAPP_FILE);
      } catch (Exception e) {
        CrashReport.getInstance()
            .log(e);
      }

      return null;
    }

    @Override protected void onPreExecute() {
      super.onPreExecute();
      pd = new ProgressDialog(DeepLinkIntentReceiver.this);
      pd.show();
      pd.setCancelable(false);
      pd.setMessage(getString(R.string.please_wait));
    }

    @Override protected void onPostExecute(Void aVoid) {
      super.onPostExecute(aVoid);
      if (pd.isShowing() && !isFinishing()) {
        pd.dismiss();
      }

      if (app != null && !app.isEmpty()) {

        /** never worked... */
      } else {
        proceed();
      }
    }
  }
}
