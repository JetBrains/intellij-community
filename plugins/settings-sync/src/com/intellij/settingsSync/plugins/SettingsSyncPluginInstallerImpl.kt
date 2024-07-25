package com.intellij.settingsSync.plugins

import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.currentOrDefaultProject
import com.intellij.openapi.updateSettings.impl.PluginDownloader
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.settingsSync.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal open class SettingsSyncPluginInstallerImpl(private val notifyErrors: Boolean) : SettingsSyncPluginInstaller {
  companion object {
    val LOG = logger<SettingsSyncPluginInstallerImpl>()
  }

  override suspend fun installPlugins(pluginsToInstall: List<PluginId>) {
    if (pluginsToInstall.isEmpty())
      return
    withModalProgress(ModalTaskOwner.guess(), SettingsSyncBundle.message("installing.plugins.indicator"), TaskCancellation.nonCancellable()) {
      val downloaders = createDownloaders(pluginsToInstall)
      installCollected(downloaders)
    }
  }

  private suspend fun installCollected(installers: List<PluginDownloader>) {
    val pluginsRequiredRestart = mutableListOf<String>()
    var settingsChanged = false
    val settings = SettingsSyncSettings.getInstance()
    for (installer in installers) {
      withContext(Dispatchers.EDT) {
        try {
          if (!install(installer)) {
            pluginsRequiredRestart.add(installer.pluginName)
          }
          LOG.info("Setting sync installed plugin ID: ${installer.id.idString}")
        }
        catch (ex: Exception) {

          // currently, we don't install plugins that have missing dependencies.
          // TODO: toposort plugin with dependencies.
          // TODO: Skip installation dependent plugins, if any dependency fails to install.
          LOG.warn("An exception occurred while installing plugin ${installer.id.idString}. Will disable syncing this plugin", ex)
          settings.setSubcategoryEnabled(SettingsCategory.PLUGINS, installer.id.idString, false)
          settingsChanged = true
        }
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

  open internal fun createDownloaders(pluginIds: Collection<PluginId>): List<PluginDownloader> {
    val compatibleUpdates = MarketplaceRequests.getLastCompatiblePluginUpdate(pluginIds.toSet())
    val retval = arrayListOf<PluginDownloader>()
    val remainingPluginIds = mutableSetOf(*pluginIds.toTypedArray())
    for (update in compatibleUpdates) {
      val pluginDescriptor = MarketplaceRequests.loadPluginDescriptor(update.pluginId, update)
      val downloader = PluginDownloader.createDownloader(pluginDescriptor)
      if (downloader.prepareToInstall(null)) {
        retval.add(downloader)
        remainingPluginIds.remove(PluginId.getId(update.externalPluginId))
      }
    }
    if (remainingPluginIds.isNotEmpty()) {
      LOG.info("Cannot find compatible updates for ${remainingPluginIds.joinToString()}")
    }
    return retval
  }
}