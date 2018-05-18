package cm.aptoide.pt.view;

import cm.aptoide.pt.app.view.NewAppViewFragment.OpenType;
import cm.aptoide.pt.search.model.SearchAdResult;

/**
 * Created by D01 on 17/05/2018.
 */

public class AppViewConfiguration {

  private final long appId;
  private final String packageName;
  private final String storeName;
  private final String storeTheme;
  private final SearchAdResult minimalAd;
  private final OpenType shouldInstall;
  private final String md5;
  private final String uName;
  private final double appc;
  private final String editorsChoice;
  private final String originTag;

  public AppViewConfiguration(long appId, String packageName, String storeName, String storeTheme,
      SearchAdResult minimalAd, OpenType shouldInstall, String md5, String uName, double appc,
      String editorsChoice, String originTag) {
    this.appId = appId;
    this.packageName = packageName;
    this.storeName = storeName;
    this.storeTheme = storeTheme;
    this.minimalAd = minimalAd;
    this.shouldInstall = shouldInstall;
    this.md5 = md5;
    this.uName = uName;
    this.appc = appc;
    this.editorsChoice = editorsChoice;
    this.originTag = originTag;
  }

  public long getAppId() {
    return appId;
  }

  public String getPackageName() {
    return packageName;
  }

  public String getStoreName() {
    return storeName;
  }

  public String getStoreTheme() {
    return storeTheme;
  }

  public SearchAdResult getMinimalAd() {
    return minimalAd;
  }

  public OpenType shouldInstall() {
    return shouldInstall;
  }

  public String getMd5() {
    return md5;
  }

  public String getuName() {
    return uName;
  }

  public double getAppc() {
    return appc;
  }

  public boolean hasIdStoreNamePackageName() {
    return (appId != -1 && storeName != null && packageName != null && packageName.isEmpty());
  }

  public boolean hasMd5() {
    return (md5 != null && !md5.isEmpty());
  }

  public boolean hasUname() {
    return (uName != null && !uName.isEmpty());
  }

  public String getEditorsChoice() {
    return editorsChoice;
  }

  public String getOriginTag() {
    return originTag;
  }
}
