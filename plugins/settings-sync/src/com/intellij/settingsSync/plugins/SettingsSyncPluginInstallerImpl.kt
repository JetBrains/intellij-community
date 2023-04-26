package com.intellij.settingsSync.plugins

import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.updateSettings.impl.PluginDownloader
import com.intellij.settingsSync.NOTIFICATION_GROUP
import com.intellij.settingsSync.SettingsSyncBundle
import com.intellij.util.Consumer
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread

internal class SettingsSyncPluginInstallerImpl(private val notifyErrors: Boolean) : SettingsSyncPluginInstaller {
  companion object {
    val LOG = logger<SettingsSyncPluginInstallerImpl>()
  }

  @RequiresBackgroundThread
  override fun installPlugins(pluginsToInstall: List<PluginId>) {
    if (pluginsToInstall.isEmpty() ||
        ApplicationManager.getApplication().isUnitTestMode // Register TestPluginManager in Unit Test Mode
    ) return
    ApplicationManager.getApplication().invokeAndWait {
      val prepareRunnable = PrepareInstallationRunnable(pluginsToInstall, notifyErrors)
      if (ProgressManager.getInstance().runProcessWithProgressSynchronously(
          prepareRunnable, SettingsSyncBundle.message("installing.plugins.indicator"), true, null)) {
        installCollected(prepareRunnable.getInstallers())
      }
    }
  }

  private fun installCollected(installers: List<PluginDownloader>) {
    val pluginsRequiredRestart = mutableListOf<String>()
    installers.forEach {
      if (!it.installDynamically(null)) {
        pluginsRequiredRestart.add("'${it.pluginName}'")
      }
      LOG.info("Setting sync installed plugin ID: ${it.id.idString}")
    }
    notifyRestartNeeded(pluginsRequiredRestart)
  }

  private fun notifyRestartNeeded(pluginsRequiredRestart: List<String>) {
    if (pluginsRequiredRestart.isEmpty())
      return
    val listOfPluginsQuoted = pluginsRequiredRestart.joinToString(
      limit = 10,
      truncated = SettingsSyncBundle.message("plugins.sync.restart.notification.more.plugins",
                                             pluginsRequiredRestart.size - 10)
    )
    val notification = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
      .createNotification(SettingsSyncBundle.message("plugins.sync.restart.notification.title"),
                          SettingsSyncBundle.message("plugins.sync.restart.notification.message",
                                                     pluginsRequiredRestart.size, listOfPluginsQuoted),
                          NotificationType.INFORMATION)
    notification.addAction(NotificationAction.create(
      SettingsSyncBundle.message("plugins.sync.restart.notification.action", ApplicationNamesInfo.getInstance().fullProductName),
      Consumer {
        val app = ApplicationManager.getApplication() as ApplicationEx
        app.restart(true)
      }))
    notification.notify(null)
  }


  private class PrepareInstallationRunnable(val pluginIds: List<PluginId>, val notifyErrors: Boolean) : Runnable {

    private val collectedInstallers = ArrayList<PluginDownloader>()

    @RequiresBackgroundThread
    override fun run() {
      val indicator = ProgressManager.getInstance().progressIndicator
      pluginIds.forEach {
        prepareToInstall(it, indicator)
        indicator.checkCanceled()
      }
    }

    @RequiresBackgroundThread
    private fun prepareToInstall(pluginId: PluginId, indicator: ProgressIndicator) {
      val descriptor = MarketplaceRequests.getInstance().getLastCompatiblePluginUpdate(pluginId, indicator = indicator)
      if (descriptor != null) {
        val downloader = PluginDownloader.createDownloader(descriptor)
        if (downloader.prepareToInstall(indicator)) {
          collectedInstallers.add(downloader)
        }
      }
      else {
        val message = SettingsSyncBundle.message("install.plugin.failed.no.compatible.notification.error.message", pluginId )
        LOG.info(message)
        if (notifyErrors) {
          NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification("", message, NotificationType.ERROR)
            .notify(null)
        }
      }
    }

    fun getInstallers() = collectedInstallers
  }
}