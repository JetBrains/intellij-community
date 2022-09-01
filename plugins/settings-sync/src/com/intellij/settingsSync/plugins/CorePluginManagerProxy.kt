package com.intellij.settingsSync.plugins

import com.intellij.ide.plugins.DisabledPluginsState
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.Disposer

class CorePluginManagerProxy : PluginManagerProxy {

  override fun getPlugins() = PluginManagerCore.getPlugins()

  override fun enablePlugin(pluginId: PluginId) {
    PluginManagerCore.enablePlugin(pluginId)
  }

  override fun disablePlugin(pluginId: PluginId) {
    PluginManagerCore.disablePlugin(pluginId)
  }

  override fun addDisablePluginListener(disabledListener: Runnable, parentDisposable: Disposable) {
    DisabledPluginsState.addDisablePluginListener(disabledListener)
    Disposer.register(parentDisposable, Disposable {
      DisabledPluginsState.removeDisablePluginListener(disabledListener)
    })
  }

  override fun getDisabledPluginIds(): Set<PluginId> {
    return DisabledPluginsState.getDisabledIds()
  }

  override fun findPlugin(pluginId: PluginId) = PluginManagerCore.findPlugin(pluginId)

  override fun createInstaller(): SettingsSyncPluginInstaller {
    return SettingsSyncPluginInstallerImpl()
  }
}