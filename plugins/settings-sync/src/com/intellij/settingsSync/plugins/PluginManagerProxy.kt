package com.intellij.settingsSync.plugins

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginEnableStateChangedListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginId

interface PluginManagerProxy {
  companion object {
    fun getInstance() = ApplicationManager.getApplication().getService(PluginManagerProxy::class.java)
  }

  fun getPlugins(): Array<IdeaPluginDescriptor>
  fun enablePlugins(plugins: Set<PluginId>)
  fun disablePlugins(plugins: Set<PluginId>)
  fun findPlugin(pluginId: PluginId): IdeaPluginDescriptor?

  fun createInstaller(notifyErrors: Boolean = false): SettingsSyncPluginInstaller

  fun addPluginStateChangedListener(listener: PluginEnableStateChangedListener, parentDisposable: Disposable)
  fun getDisabledPluginIds(): Set<PluginId>

  fun isEssential(pluginId: PluginId): Boolean

  fun isIncompatible(plugin: IdeaPluginDescriptor): Boolean
}