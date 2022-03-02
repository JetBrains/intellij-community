package com.intellij.settingsSync.plugins

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId

class CorePluginManagerProxy: PluginManagerProxy {

  override fun getPlugins() = PluginManagerCore.getPlugins()

  override fun enablePlugin(pluginId: PluginId) {
    PluginManagerCore.enablePlugin(pluginId)
  }

  override fun disablePlugin(pluginId: PluginId) {
    PluginManagerCore.disablePlugin(pluginId)
  }

  override fun findPlugin(pluginId: PluginId) = PluginManagerCore.findPlugin(pluginId)

  override fun createInstaller(): SettingsSyncPluginInstaller {
    return SettingsSyncPluginInstallerImpl()
  }
}