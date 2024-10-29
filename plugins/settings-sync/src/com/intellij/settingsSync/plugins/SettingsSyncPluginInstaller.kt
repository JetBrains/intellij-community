package com.intellij.settingsSync.plugins

import com.intellij.openapi.extensions.PluginId

interface SettingsSyncPluginInstaller {
  suspend fun installPlugins(pluginsToInstall: List<PluginId>)
}