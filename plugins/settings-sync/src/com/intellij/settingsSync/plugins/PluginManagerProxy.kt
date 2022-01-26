package com.intellij.settingsSync.plugins

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginId

interface PluginManagerProxy {
  companion object {
    fun getInstance() = ApplicationManager.getApplication().getService(PluginManagerProxy::class.java)
  }

  fun getPlugins(): Array<IdeaPluginDescriptor>
  fun enablePlugin(pluginId: PluginId)
  fun disablePlugin(pluginId: PluginId)
  fun findPlugin(pluginId: PluginId): IdeaPluginDescriptor?

  fun createInstaller(): SettingsSyncPluginInstaller
}