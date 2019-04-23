/*
 * Copyright (c) 2017.
 * Modified by Marcelo Benites on 18/01/2017.
 */

package cm.aptoide.pt.view;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.internal.BottomNavigationItemView;
import android.support.design.internal.BottomNavigationMenuView;
import android.support.design.widget.Snackbar;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import cm.aptoide.pt.AptoideApplication;
import cm.aptoide.pt.BuildConfig;
import cm.aptoide.pt.R;
import cm.aptoide.pt.actions.PermissionService;
import cm.aptoide.pt.bottomNavigation.BottomNavigationActivity;
import cm.aptoide.pt.bottomNavigation.BottomNavigationMapper;
import cm.aptoide.pt.install.InstallManager;
import cm.aptoide.pt.presenter.MainView;
import cm.aptoide.pt.presenter.Presenter;
import cm.aptoide.pt.util.MarketResourceFormatter;
import cm.aptoide.pt.utils.AptoideUtils;
import cm.aptoide.pt.utils.design.ShowMessage;
import com.ironsource.mediationsdk.IronSource;
import com.jakewharton.rxrelay.PublishRelay;
import com.mopub.common.MoPub;
import com.mopub.common.SdkConfiguration;
import com.mopub.common.logging.MoPubLog;
import com.mopub.nativeads.AppLovinBaseAdapterConfiguration;
import com.mopub.nativeads.AppnextBaseAdapterConfiguration;
import com.mopub.nativeads.InMobiBaseAdapterConfiguration;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import rx.Observable;
import rx.subjects.PublishSubject;

public class MainActivity extends BottomNavigationActivity
    implements MainView, DeepLinkManager.DeepLinkMessages {

  @Inject Presenter presenter;
  @Inject Resources resources;
  @Inject MarketResourceFormatter marketResourceFormatter;
  private InstallManager installManager;
  private View snackBarLayout;
  private PublishRelay<Void> installErrorsDismissEvent;
  private Snackbar snackbar;
  private View updatesBadge;
  private TextView updatesNumber;
  private ProgressDialog autoUpdateDialog;
  private PublishSubject<PermissionService> autoUpdateDialogSubject;

  @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getActivityComponent().inject(this);
    final AptoideApplication application = (AptoideApplication) getApplicationContext();
    installManager = application.getInstallManager();
    snackBarLayout = findViewById(R.id.snackbar_layout);
    installErrorsDismissEvent = PublishRelay.create();
    autoUpdateDialogSubject = PublishSubject.create();

    initializeMoPub();
    initializeAdsMediation();
    setupUpdatesNotification();

    attachPresenter(presenter);
  }

  @Override protected void onDestroy() {
    autoUpdateDialogSubject = null;
    autoUpdateDialog = null;
    installErrorsDismissEvent = null;
    installManager = null;
    updatesBadge = null;
    snackBarLayout = null;
    snackbar = null;
    super.onDestroy();
  }

  private void initializeAdsMediation() {
    IronSource.init(this, BuildConfig.MOPUB_IRONSOURCE_APPLICATION_ID);
  }

  @Override protected void onStart() {
    super.onStart();
    MoPub.onStart(this);
  }

  @Override protected void onResume() {
    super.onResume();
    MoPub.onResume(this);
    IronSource.onResume(this);
  }

  @Override protected void onPause() {
    super.onPause();
    MoPub.onPause(this);
    IronSource.onPause(this);
  }

  @Override protected void onStop() {
    super.onStop();
    MoPub.onStop(this);
  }

  @Override protected void onRestart() {
    super.onRestart();
    MoPub.onRestart(this);
  }

  private void setupUpdatesNotification() {
    BottomNavigationMenuView appsView =
        (BottomNavigationMenuView) bottomNavigationView.getChildAt(0);
    BottomNavigationItemView appsItemView =
        (BottomNavigationItemView) appsView.getChildAt(BottomNavigationMapper.APPS_POSITION);

    updatesBadge = LayoutInflater.from(this)
        .inflate(R.layout.updates_badge, appsView, false);
    updatesNumber = (TextView) updatesBadge.findViewById(R.id.updates_badge);
    appsItemView.addView(updatesBadge);
    appsItemView.setVisibility(View.VISIBLE);
  }

  @Override public void showInstallationError(int numberOfErrors) {
    String title;
    if (numberOfErrors == 1) {
      title = getString(R.string.generalscreen_short_root_install_single_app_timeout_error_message);
    } else {
      title = getString(R.string.generalscreen_short_root_install_timeout_error_message,
          numberOfErrors);
    }

    Snackbar.Callback snackbarCallback = new Snackbar.Callback() {
      @Override public void onDismissed(Snackbar snackbar, int event) {
        super.onDismissed(snackbar, event);
        if (event == DISMISS_EVENT_SWIPE) {
          installErrorsDismissEvent.call(null);
        }
      }
    };
    snackbar = Snackbar.make(snackBarLayout, title, Snackbar.LENGTH_INDEFINITE)
        .setAction(R.string.generalscreen_short_root_install_timeout_error_action,
            view -> installManager.retryTimedOutInstallations()
                .andThen(installManager.cleanTimedOutInstalls())
                .subscribe())
        .addCallback(snackbarCallback);
    snackbar.show();
  }

  @Override public void dismissInstallationError() {
    if (snackbar != null) {
      snackbar.dismiss();
    }
  }

  @Override public void showInstallationSuccessMessage() {
    ShowMessage.asSnack(snackBarLayout, R.string.generalscreen_short_root_install_success_install);
  }

  @Override public Observable<Void> getInstallErrorsDismiss() {
    return installErrorsDismissEvent;
  }

  @Override public Intent getIntentAfterCreate() {
    return getIntent();
  }

  @Override public void showUpdatesNumber(Integer updates) {
    updatesBadge.setVisibility(View.VISIBLE);
    updatesNumber.setText(String.valueOf(updates));
  }

  @Override public void hideUpdatesBadge() {
    updatesBadge.setVisibility(View.GONE);
  }

  @Override public Observable<PermissionService> autoUpdateDialogCreated() {
    return autoUpdateDialogSubject;
  }

  @Override public void requestAutoUpdate() {
    AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
    final AlertDialog updateSelfDialog = dialogBuilder.create();
    updateSelfDialog.setTitle(getText(R.string.update_self_title));
    updateSelfDialog.setIcon(R.mipmap.ic_launcher);
    updateSelfDialog.setMessage(
        marketResourceFormatter.formatString(getApplicationContext(), R.string.update_self_msg));
    updateSelfDialog.setCancelable(false);
    updateSelfDialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(android.R.string.yes),
        (arg0, arg1) -> {
          autoUpdateDialog = new ProgressDialog(this);
          autoUpdateDialog.setMessage(getString(R.string.retrieving_update));
          autoUpdateDialog.show();
          autoUpdateDialogSubject.onNext(this);
        });
    updateSelfDialog.setButton(Dialog.BUTTON_NEGATIVE, getString(android.R.string.no),
        (dialog, arg1) -> {
          dialog.dismiss();
        });
    if (is_resumed()) {
      updateSelfDialog.show();
    }
  }

  @Override public void showUnknownErrorMessage() {
    Snackbar.make(findViewById(android.R.id.content), R.string.unknown_error, Snackbar.LENGTH_SHORT)
        .show();
  }

  @Override public void dismissAutoUpdateDialog() {
    if (autoUpdateDialog != null && autoUpdateDialog.isShowing()) {
      autoUpdateDialog.dismiss();
    }
  }

  public void initializeMoPub() {
    SdkConfiguration sdkConfiguration = new SdkConfiguration.Builder(
        BuildConfig.MOPUB_BANNER_50_HOME_PLACEMENT_ID).withAdditionalNetwork(
        AppLovinBaseAdapterConfiguration.class.toString())
        .withMediatedNetworkConfiguration(AppLovinBaseAdapterConfiguration.class.toString(),
            getMediationNetworkConfiguration(BuildConfig.MOPUB_BANNER_50_HOME_PLACEMENT_ID))
        .withAdditionalNetwork(AppnextBaseAdapterConfiguration.class.toString())
        .withMediatedNetworkConfiguration(AppnextBaseAdapterConfiguration.class.toString(),
            getMediationNetworkConfiguration(BuildConfig.MOPUB_BANNER_50_HOME_PLACEMENT_ID))
        .withMediatedNetworkConfiguration(InMobiBaseAdapterConfiguration.class.toString(),
            getMediationNetworkConfiguration(BuildConfig.MOPUB_BANNER_50_HOME_PLACEMENT_ID))
        .withLogLevel(MoPubLog.LogLevel.DEBUG)
        .build();

    MoPub.initializeSdk(this, sdkConfiguration, null);
  }

  @NonNull
  private Map<String, String> getMediationNetworkConfiguration(String mediatedNetworkPlacementId) {
    Map<String, String> mediationNetworkConfiguration = new HashMap<>();
    mediationNetworkConfiguration.put("Placement_Id", mediatedNetworkPlacementId);
    return mediationNetworkConfiguration;
  }

  @Override public void showStoreAlreadyAdded() {
    ShowMessage.asLongSnack(this, getString(R.string.store_already_added));
  }

  @Override public void showStoreFollowed(String storeName) {
    ShowMessage.asLongSnack(this,
        AptoideUtils.StringU.getFormattedString(R.string.store_followed, getResources(),
            storeName));
  }
}
