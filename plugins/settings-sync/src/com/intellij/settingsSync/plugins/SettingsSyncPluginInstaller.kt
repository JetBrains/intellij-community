package com.intellij.settingsSync.plugins

import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.updateSettings.impl.PluginDownloader
import com.intellij.settingsSync.SettingsSyncBundle

internal class SettingsSyncPluginInstaller {
  private val pluginsIds = ArrayList<PluginId>()

  fun addPluginId(pluginId: PluginId) {
    pluginsIds.add(pluginId)
  }

  fun installUnderProgress() {
    if (pluginsIds.isEmpty()) return
    ProgressManager.getInstance().run(InstallationTask(pluginsIds))
  }

  private class InstallationTask(val pluginIds: List<PluginId>) :
    Task.Backgroundable(null, SettingsSyncBundle.message("installing.plugins.indicator")) {

    private val collectedInstallers = ArrayList<PluginDownloader>()

    override fun run(indicator: ProgressIndicator) {
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

    override fun onFinished() {
      collectedInstallers.forEach {
        it.installDynamically(null)
        // TODO<rv> Notify a user if the IDE needs a restart(?)
      }
    }
  }

}