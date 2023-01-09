package com.intellij.settingsSync.config

import org.jetbrains.annotations.Nls

internal interface SettingsSyncSubcategoryGroup {
  fun getDescriptors() : List<SettingsSyncSubcategoryDescriptor>

  /**
   * Returns `true` if [getDescriptors] covers all the possible synchronizable elements of the group. `false` if there are implicit
   * elements not covered by the returned list of descriptors, in other words a user can't disable the entire group by unselecting the
   * explicitly described items.
   */
  fun isComplete() : Boolean = true
}

internal data class SettingsSyncSubcategoryDescriptor(
  val name: @Nls String,
  val id: String,
  var isSelected: Boolean,
  var isSubGroupEnd: Boolean
) {
  override fun toString(): String {
    return name
  }
}
