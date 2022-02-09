package com.intellij.settingsSync.config

import org.jetbrains.annotations.Nls

internal interface SettingsSyncSubcategoryGroup {
  fun getDescriptors() : List<SettingsSyncSubcategoryDescriptor>

  fun isComplete() : Boolean = true
}

internal data class SettingsSyncSubcategoryDescriptor(
  val name: @Nls String,
  val id: String,
  var isSelected: Boolean
) {
  override fun toString(): String {
    return name
  }
}
