package com.intellij.settingsSync.config

import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.SettingsCategory.*
import com.intellij.settingsSync.SettingsSyncBundle.message
import com.intellij.settingsSync.SettingsSyncSettings
import org.jetbrains.annotations.Nls
import java.util.*

internal class SettingsCategoryDescriptor(
  private val category : SettingsCategory,
  val secondaryGroup: SettingsSyncSubcategoryGroup? = null
) {

  companion object {
    private val DESCRIPTORS : List<SettingsCategoryDescriptor> = listOf(
      SettingsCategoryDescriptor(UI, SettingsSyncUiGroup()),
      SettingsCategoryDescriptor(KEYMAP),
      SettingsCategoryDescriptor(CODE),
      SettingsCategoryDescriptor(PLUGINS, SettingsSyncPluginsGroup()),
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
    if (secondaryGroup != null) {
      secondaryGroup.getDescriptors().forEach {
        it.isSelected = isSynchronized && SettingsSyncSettings.getInstance().isSubcategoryEnabled(category, it.id)
      }
    }
  }

  fun apply() {
    if (secondaryGroup != null) {
      secondaryGroup.getDescriptors().forEach {
        // !isSynchronized not store disabled states individually
        SettingsSyncSettings.getInstance().setSubcategoryEnabled(category, it.id, !isSynchronized || it.isSelected)
      }
    }
    SettingsSyncSettings.getInstance().setCategoryEnabled(category, isSynchronized)
  }

  fun isModified() : Boolean {
    if (isSynchronized != SettingsSyncSettings.getInstance().isCategoryEnabled(category)) return true
    if (secondaryGroup != null && isSynchronized) {
      secondaryGroup.getDescriptors().forEach {
        if (it.isSelected != SettingsSyncSettings.getInstance().isSubcategoryEnabled(category, it.id)) return true
      }
    }
    return false
  }


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
      return "settings.category." + category.name.lowercase(Locale.getDefault())
    }
}