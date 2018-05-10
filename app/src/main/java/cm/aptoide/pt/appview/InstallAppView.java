package cm.aptoide.pt.appview;

import cm.aptoide.pt.app.DownloadAppViewModel;
import cm.aptoide.pt.presenter.View;
import rx.Observable;

/**
 * Created by filipegoncalves on 5/7/18.
 */

public interface InstallAppView extends View {

  Observable<Void> installAppClick();

  Observable<Boolean> showRootInstallWarningPopup();

  void showDownloadAppModel(DownloadAppViewModel model);
}
