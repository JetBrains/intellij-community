package com.intellij.settingsSync.plugins

import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.updateSettings.impl.PluginDownloader
import com.intellij.settingsSync.NOTIFICATION_GROUP
import com.intellij.settingsSync.SettingsSyncBundle
import com.intellij.util.Consumer
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread

internal class SettingsSyncPluginInstallerImpl : SettingsSyncPluginInstaller {
  private val pluginsIds = ArrayList<PluginId>()

  override fun addPluginId(pluginId: PluginId) {
    pluginsIds.add(pluginId)
  }

  @RequiresBackgroundThread
  override fun installPlugins() {
    if (pluginsIds.isEmpty() ||
        ApplicationManager.getApplication().isUnitTestMode // Register TestPluginManager in Unit Test Mode
    ) return
    ApplicationManager.getApplication().invokeAndWait {
      val prepareRunnable = PrepareInstallationRunnable(pluginsIds)
      if (ProgressManager.getInstance().runProcessWithProgressSynchronously(
          prepareRunnable, SettingsSyncBundle.message("installing.plugins.indicator"), true, null)) {
        installCollected(prepareRunnable.getInstallers())
      }
    }
  }

  private fun installCollected(installers: List<PluginDownloader>) {
    var isRestartNeeded = false
    installers.forEach {
      if (!it.installDynamically(null)) {
        isRestartNeeded = true
      }
    }
    if (isRestartNeeded) notifyRestartNeeded()
  }

  private fun notifyRestartNeeded() {
    val notification = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
      .createNotification(SettingsSyncBundle.message("plugins.sync.restart.notification.title"),
                          SettingsSyncBundle.message("plugins.sync.restart.notification.message"),
                          NotificationType.INFORMATION)
    notification.addAction(NotificationAction.create(
      SettingsSyncBundle.message("plugins.sync.restart.notification.action", ApplicationNamesInfo.getInstance().fullProductName),
      Consumer {
        val app = ApplicationManager.getApplication() as ApplicationEx
        app.restart(true)
      }))
    notification.notify(null)
  }


  private class PrepareInstallationRunnable(val pluginIds: List<PluginId>) : Runnable {

    private val collectedInstallers = ArrayList<PluginDownloader>()

    override fun run() {
      val indicator = ProgressManager.getInstance().progressIndicator
      pluginIds.forEach {
        prepareToInstall(it, indicator)
        indicator.checkCanceled()
      }
    }

    private fun prepareToInstall(pluginId: PluginId, indicator: ProgressIndicator) {
      val descriptor = MarketplaceRequests.getInstance().getLastCompatiblePluginUpdate(pluginId, indicator = indicator)
      if (descriptor != null) {
        val downloader = PluginDownloader.createDownloader(descriptor)
        if (downloader.prepareToInstall(indicator)) {
          collectedInstallers.add(downloader)
        }
      }
    }

    fun getInstallers() = collectedInstallers
  }

}