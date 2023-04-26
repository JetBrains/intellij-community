package com.intellij.settingsSync.plugins

import com.intellij.ide.plugins.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.Disposer

class CorePluginManagerProxy : AbstractPluginManagerProxy() {

  override val pluginEnabler: PluginEnabler
    get() = PluginEnabler.getInstance()

  override fun isDescriptorEssential(pluginId: PluginId): Boolean =
    (ApplicationInfo.getInstance() as ApplicationInfoEx).isEssentialPlugin(pluginId)

  override fun getPlugins() = PluginManagerCore.getPlugins()

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