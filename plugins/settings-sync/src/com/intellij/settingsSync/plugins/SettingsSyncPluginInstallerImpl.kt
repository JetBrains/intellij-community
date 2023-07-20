package com.intellij.settingsSync.plugins

import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.updateSettings.impl.PluginDownloader
import com.intellij.settingsSync.NOTIFICATION_GROUP
import com.intellij.settingsSync.SettingsSyncBundle
import com.intellij.settingsSync.SettingsSyncEvents
import com.intellij.settingsSync.SettingsSyncSettings
import com.intellij.util.Consumer
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread

internal open class SettingsSyncPluginInstallerImpl(private val notifyErrors: Boolean) : SettingsSyncPluginInstaller {
  companion object {
    val LOG = logger<SettingsSyncPluginInstallerImpl>()
  }

  @RequiresBackgroundThread
  override fun installPlugins(pluginsToInstall: List<PluginId>) {
    if (pluginsToInstall.isEmpty())
      return
    ApplicationManager.getApplication().invokeAndWait {
      val prepareRunnable = PrepareInstallationRunnable(pluginsToInstall) { pluginId, indicator -> createDownloader(pluginId, indicator) }
      if (ProgressManager.getInstance().runProcessWithProgressSynchronously(
          prepareRunnable, SettingsSyncBundle.message("installing.plugins.indicator"), true, null)) {
        installCollected(prepareRunnable.getInstallers())
      }
    }
  }

  private fun installCollected(installers: List<PluginDownloader>) {
    val pluginsRequiredRestart = mutableListOf<String>()
    var settingsChanged = false
    val settings = SettingsSyncSettings.getInstance()
    for (installer in installers) {
      try {
        if (!install(installer)) {
          pluginsRequiredRestart.add("'${installer.pluginName}'")
        }
        LOG.info("Setting sync installed plugin ID: ${installer.id.idString}")
      } catch (ex: Exception) {
        // currently, we don't install plugins that have missing dependencies.
        // TODO: toposort plugin with dependencies.
        // TODO: Skip installation dependent plugins, if any dependency fails to install.
        LOG.warn("An exception occurred while installing plugin ${installer.id.idString}. Will disable syncing this plugin")
        settings.setSubcategoryEnabled(SettingsCategory.PLUGINS, installer.id.idString, false)
        settingsChanged = true
      }
    }
    if (settingsChanged){
      SettingsSyncEvents.getInstance().fireCategoriesChanged()
    }
    notifyRestartNeeded(pluginsRequiredRestart)
  }

  open internal fun install(installer: PluginDownloader): Boolean = installer.installDynamically(null)

  open internal fun createDownloader(pluginId: PluginId, indicator: ProgressIndicator): PluginDownloader? {
    val descriptor = MarketplaceRequests.getInstance().getLastCompatiblePluginUpdate(pluginId, indicator = indicator)
    if (descriptor != null) {
      val downloader = PluginDownloader.createDownloader(descriptor)
      if (downloader.prepareToInstall(indicator)) {
        return downloader
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
    return null
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


  internal class PrepareInstallationRunnable(
    val pluginIds: List<PluginId>,
    val dwnldPreparer: (pluginId: PluginId, indicator: ProgressIndicator) -> PluginDownloader?
  ) : Runnable {

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
      dwnldPreparer(pluginId, indicator) ?.also {
          collectedInstallers.add(it)
      }
    }

    fun getInstallers() = collectedInstallers
  }
}