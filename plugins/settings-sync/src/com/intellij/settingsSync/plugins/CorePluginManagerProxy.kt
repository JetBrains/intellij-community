package com.intellij.settingsSync.plugins

import com.intellij.ide.plugins.DisabledPluginsState
import com.intellij.ide.plugins.PluginEnabler
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.Disposer

class CorePluginManagerProxy : PluginManagerProxy {

  override fun getPlugins() = PluginManagerCore.getPlugins()

  override fun enablePlugins(plugins: Set<PluginId>) {
    PluginEnabler.getInstance().enableById(plugins)
  }

  override fun disablePlugins(plugins: Set<PluginId>) {
    PluginEnabler.getInstance().disableById(plugins)
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

  override fun createInstaller(notifyErrors: Boolean): SettingsSyncPluginInstaller {
    return SettingsSyncPluginInstallerImpl(notifyErrors)
  }
}