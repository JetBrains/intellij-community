package com.intellij.settingsSync.plugins

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.openapi.components.SettingsCategory

internal object SettingsSyncPluginCategoryFinder {

  val UI_CATEGORIES = setOf(
    "Theme",
    "Editor Color Schemes")

  val UI_EXTENSIONS = setOf(
    "com.intellij.bundledColorScheme",
    "com.intellij.themeProvider"
  )

  fun getPluginCategory(descriptor: IdeaPluginDescriptor): SettingsCategory {
    if (UI_CATEGORIES.contains(descriptor.category)|| containsOnlyUIExtensions(descriptor)) {
      return SettingsCategory.UI
    }
    return SettingsCategory.PLUGINS
  }

  private fun containsOnlyUIExtensions(descriptor: IdeaPluginDescriptor) : Boolean {
    if (descriptor is IdeaPluginDescriptorImpl) {
      return descriptor.epNameToExtensions?.all {
        UI_EXTENSIONS.contains(it.key)
      } ?: false
    }
    return false
  }
}