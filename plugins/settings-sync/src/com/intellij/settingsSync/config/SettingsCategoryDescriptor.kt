package com.intellij.settingsSync.config

import com.intellij.openapi.components.ComponentCategory
import com.intellij.openapi.components.ComponentCategory.*
import com.intellij.settingsSync.SettingsSyncBundle.message
import com.intellij.settingsSync.SettingsSyncSettings
import org.jetbrains.annotations.Nls

internal class SettingsCategoryDescriptor(
  private val category : ComponentCategory
) {

  companion object {
    private val DESCRIPTORS : List<SettingsCategoryDescriptor> = listOf(
      SettingsCategoryDescriptor(UI),
      SettingsCategoryDescriptor(KEYMAP),
      SettingsCategoryDescriptor(CODE),
      SettingsCategoryDescriptor(PLUGINS),
      SettingsCategoryDescriptor(TOOLS),
      SettingsCategoryDescriptor(SYSTEM),
    )

    fun listAll() : List<SettingsCategoryDescriptor> {
      return DESCRIPTORS
    }
  }

  var isSynchronized: Boolean = true

  fun reset() {
    isSynchronized = SettingsSyncSettings.getInstance().isCategoryEnabled(category)
  }

  fun apply() {
    SettingsSyncSettings.getInstance().setCategoryEnabled(category, isSynchronized)
  }

  fun isModified() : Boolean = isSynchronized != SettingsSyncSettings.getInstance().isCategoryEnabled(category)


  val name: @Nls String
    get() {
      return message("${categoryKey}.name")
    }

  val description: @Nls String
  get() {
    return message("${categoryKey}.description")
  }

  private val categoryKey: String
    get() {
      return "settings.category." + category.name.toLowerCase()
    }
}