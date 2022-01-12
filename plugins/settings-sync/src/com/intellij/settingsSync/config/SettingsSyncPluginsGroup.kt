package com.intellij.settingsSync.config

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.settingsSync.SettingsSyncBundle.message
import org.jetbrains.annotations.Nls

internal const val BUNDLED_PLUGINS_ID = "bundled"

internal class SettingsSyncPluginsGroup : SettingsSyncSubcategoryGroup {
  private val storedDescriptors = HashMap<String, SettingsSyncSubcategoryDescriptor>()

  override fun getDescriptors(): List<SettingsSyncSubcategoryDescriptor> {
    val descriptors = ArrayList<SettingsSyncSubcategoryDescriptor>()
    descriptors.add(getOrCreateDescriptor(message("plugins.bundled"), BUNDLED_PLUGINS_ID))
    PluginManagerCore.getPlugins().forEach {
      if (!it.isBundled) {
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
      val descriptor = SettingsSyncSubcategoryDescriptor(name, id, true)
      storedDescriptors[id] = descriptor
      descriptor
    }
  }
}