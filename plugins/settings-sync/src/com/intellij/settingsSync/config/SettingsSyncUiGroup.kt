package com.intellij.settingsSync.config

import com.intellij.settingsSync.SettingsSyncBundle

internal class SettingsSyncUiGroup : SettingsSyncSubcategoryGroup {

  private val descriptors = listOf(SettingsSyncSubcategoryDescriptor(SettingsSyncBundle.message("settings.category.ui.editor.font"), EDITOR_FONT_ID, false))

  companion object {
    const val EDITOR_FONT_ID = "editorFont"
  }

  override fun getDescriptors(): List<SettingsSyncSubcategoryDescriptor> {
    return descriptors
  }

  override fun isComplete() = false
}