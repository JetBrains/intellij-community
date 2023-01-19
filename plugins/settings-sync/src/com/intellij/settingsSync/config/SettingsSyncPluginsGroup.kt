package com.intellij.settingsSync.config

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.components.SettingsCategory
import com.intellij.settingsSync.SettingsSyncBundle.message
import com.intellij.settingsSync.plugins.SettingsSyncPluginCategoryFinder
import org.jetbrains.annotations.Nls

internal const val BUNDLED_PLUGINS_ID = "bundled"

internal class SettingsSyncPluginsGroup : SettingsSyncSubcategoryGroup {
  private val storedDescriptors = HashMap<String, SettingsSyncSubcategoryDescriptor>()

  override fun getDescriptors(): List<SettingsSyncSubcategoryDescriptor> {
    val descriptors = ArrayList<SettingsSyncSubcategoryDescriptor>()
    val bundledPluginsDescriptor = getOrCreateDescriptor(message("plugins.bundled"), BUNDLED_PLUGINS_ID)
    descriptors.add(bundledPluginsDescriptor)
    PluginManagerCore.getPlugins().forEach {
      if (!it.isBundled && SettingsSyncPluginCategoryFinder.getPluginCategory(it) == SettingsCategory.PLUGINS) {
        bundledPluginsDescriptor.isSubGroupEnd = true
        descriptors.add(getOrCreateDescriptor(it.name, it.pluginId.idString))
      }
    }
    return descriptors
  }

  private fun getOrCreateDescriptor(name : @Nls String, id : String) : SettingsSyncSubcategoryDescriptor {
    return if (storedDescriptors.containsKey(id)) {
      storedDescriptors[id]!!
    }
    else {
      val descriptor = SettingsSyncSubcategoryDescriptor(name, id, true, false)
      storedDescriptors[id] = descriptor
      descriptor
    }
  }
}