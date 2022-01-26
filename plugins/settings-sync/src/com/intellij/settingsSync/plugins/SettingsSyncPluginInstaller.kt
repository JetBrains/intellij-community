package com.intellij.settingsSync.plugins

import com.intellij.openapi.extensions.PluginId

interface SettingsSyncPluginInstaller {
  fun addPluginId(pluginId: PluginId)
  fun startInstallation()
}