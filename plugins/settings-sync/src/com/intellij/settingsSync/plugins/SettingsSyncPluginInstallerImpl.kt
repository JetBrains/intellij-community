package com.intellij.settingsSync.plugins

import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.updateSettings.impl.PluginDownloader
import com.intellij.settingsSync.*
import com.intellij.settingsSync.NOTIFICATION_GROUP
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread

internal open class SettingsSyncPluginInstallerImpl(private val notifyErrors: Boolean) : SettingsSyncPluginInstaller {
  companion object {
    val LOG = logger<SettingsSyncPluginInstallerImpl>()
  }

  @RequiresBackgroundThread
  override fun installPlugins(pluginsToInstall: List<PluginId>) {
    if (pluginsToInstall.isEmpty())
      return
    val pluginInstallation = object : Task.Backgroundable(null, SettingsSyncBundle.message("installing.plugins.indicator"), true) {
      override fun run(indicator: ProgressIndicator) {
        val prepareRunnable = PrepareInstallationRunnable(pluginsToInstall, indicator) { pluginId, indicator -> createDownloader(pluginId, indicator) }
        prepareRunnable.run()
        installCollected(prepareRunnable.getInstallers())
      }
    }
    ProgressManager.getInstance().run(pluginInstallation)
  }

  private fun installCollected(installers: List<PluginDownloader>) {
    val pluginsRequiredRestart = mutableListOf<String>()
    var settingsChanged = false
    val settings = SettingsSyncSettings.getInstance()
    for (installer in installers) {
      try {
        if (!install(installer)) {
          pluginsRequiredRestart.add(installer.pluginName)
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
    if (pluginsRequiredRestart.size > 0) {
      SettingsSyncEvents.getInstance().fireRestartRequired(RestartForPluginInstall(pluginsRequiredRestart))
    }
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

  internal class PrepareInstallationRunnable(
    private val pluginIds: List<PluginId>,
    private val indicator: ProgressIndicator,
    val dwnldPreparer: (pluginId: PluginId, indicator: ProgressIndicator) -> PluginDownloader?
  ) : Runnable {

    private val collectedInstallers = ArrayList<PluginDownloader>()

    @RequiresBackgroundThread
    override fun run() {
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