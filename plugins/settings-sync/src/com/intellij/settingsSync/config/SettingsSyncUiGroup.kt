package com.intellij.settingsSync.config

import com.intellij.settingsSync.SettingsSyncBundle

const val EDITOR_FONT_SUBCATEGORY_ID = "editorFont"

internal class SettingsSyncUiGroup : SettingsSyncSubcategoryGroup {

  private val descriptors = listOf(SettingsSyncSubcategoryDescriptor(SettingsSyncBundle.message("settings.category.ui.editor.font"),
                                                                     EDITOR_FONT_SUBCATEGORY_ID, false))

  override fun getDescriptors(): List<SettingsSyncSubcategoryDescriptor> {
    return descriptors
  }

  override fun isComplete() = false
}