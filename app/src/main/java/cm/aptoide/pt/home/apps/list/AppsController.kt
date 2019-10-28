package cm.aptoide.pt.home.apps.list

import cm.aptoide.pt.R
import cm.aptoide.pt.home.apps.*
import cm.aptoide.pt.home.apps.list.models.AppCardModel_
import cm.aptoide.pt.home.apps.list.models.AppcHeaderModel_
import cm.aptoide.pt.home.apps.list.models.TitleModel_
import cm.aptoide.pt.home.apps.model.AppcUpdateApp
import cm.aptoide.pt.home.apps.model.DownloadApp
import cm.aptoide.pt.home.apps.model.InstalledApp
import cm.aptoide.pt.home.apps.model.UpdateApp
import com.airbnb.epoxy.Typed4EpoxyController
import rx.subjects.PublishSubject

class AppsController :
    Typed4EpoxyController<List<UpdateApp>, List<InstalledApp>, List<AppcUpdateApp>, List<DownloadApp>>() {

  private val appEventListener = PublishSubject.create<AppClick>()
  val updateAllEvent = PublishSubject.create<Void>()

  override fun buildModels(updates: List<UpdateApp>, installedApps: List<InstalledApp>,
                           migrations: List<AppcUpdateApp>,
                           downloads: List<DownloadApp>) {

    // Appc migrations
    AppcHeaderModel_()
        .id("appc_migration", "header")
        .reward(getPromotionValue(migrations))
        .addIf(migrations.isNotEmpty(), this)

    for (migration in migrations) {
      AppCardModel_()
          .id("appc_migration", migration.identifier)
          .application(migration)
          .eventSubject(appEventListener)
          .addTo(this)
    }

    // Downloads
    TitleModel_()
        .id("downloads", "header")
        .title(R.string.apps_title_downloads_header)
        .shouldShowButton(false)
        .addIf(downloads.isNotEmpty(), this)

    for (download in downloads) {
      AppCardModel_()
          .id("downloads", download.identifier)
          .application(download)
          .eventSubject(appEventListener)
          .addTo(this)
    }

    // Updates
    TitleModel_()
        .id("updates", "header")
        .title(R.string.apps_title_updates_header)
        .shouldShowButton(true)
        .eventSubject(updateAllEvent)
        .addIf(updates.isNotEmpty(), this)

    for (update in updates) {
      AppCardModel_()
          .id("updates", update.identifier)
          .application(update)
          .eventSubject(appEventListener)
          .addTo(this)
    }

    // Installed
    TitleModel_()
        .id("installed", "header")
        .title(R.string.apps_title_installed_apps_header)
        .shouldShowButton(false)
        .addIf(installedApps.isNotEmpty(), this)

    for (installed in installedApps) {
      AppCardModel_()
          .id("installed", installed.identifier)
          .application(installed)
          .eventSubject(appEventListener)
          .addTo(this)
    }
  }

  private fun getPromotionValue(migrations: List<AppcUpdateApp>): Float {
    var promotionValue = 0f
    for (migration in migrations) {
      if (migration.hasPromotion()) {
        promotionValue += migration.appcReward
      }
    }
    return promotionValue
  }


  /**
   * This is overriden so that there's named arguments instead of data1, data2, data3...
   */
  override fun setData(updates: List<UpdateApp>, installedApps: List<InstalledApp>,
                       migrations: List<AppcUpdateApp>,
                       downloads: List<DownloadApp>) {
    super.setData(updates, installedApps, migrations, downloads)
  }

}