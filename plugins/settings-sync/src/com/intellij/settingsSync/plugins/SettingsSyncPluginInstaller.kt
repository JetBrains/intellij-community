package com.intellij.settingsSync.plugins

import com.intellij.openapi.extensions.PluginId

interface SettingsSyncPluginInstaller {
  fun installPlugins(pluginsToInstall: List<PluginId>)
}