package com.intellij.settingsSync

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.settingsSync.SettingsSyncSettings.Companion.FILE_SPEC
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.*

internal fun interface SettingsSyncEnabledStateListener : EventListener {
  fun enabledStateChanged(syncEnabled: Boolean)
}

@State(name = "SettingsSyncSettings", storages = [Storage(FILE_SPEC)])
@ApiStatus.Internal
class SettingsSyncSettings :
  SimplePersistentStateComponent<SettingsSyncSettings.SettingsSyncSettingsState>(SettingsSyncSettingsState())
{

  companion object {
    fun getInstance() = ApplicationManager.getApplication().getService(SettingsSyncSettings::class.java)

    const val FILE_SPEC = "settingsSync.xml"
  }

  var migrationFromOldStorageChecked: Boolean
    get() = state.migrationFromOldStorageChecked
    set(value) {
      state.migrationFromOldStorageChecked = value
    }

  var syncEnabled
    get() = state.syncEnabled
    set(value) {
      state.syncEnabled = value
      fireSettingsStateChanged(value)
    }

  private fun fireSettingsStateChanged(syncEnabled: Boolean) {
    SettingsSyncEvents.getInstance().fireEnabledStateChanged(syncEnabled)
  }

  fun isCategoryEnabled(category: SettingsCategory) = !state.disabledCategories.contains(category)

  fun setCategoryEnabled(category: SettingsCategory, isEnabled: Boolean) {
    if (isEnabled) {
      state.disabledCategories.remove(category)
    }
    else {
      if (!state.disabledCategories.contains(category)) {
        state.disabledCategories.add(category)
        state.disabledCategories.sort()
      }
    }
  }

  fun isSubcategoryEnabled(category: SettingsCategory, subcategoryId: String): Boolean {
    val disabled = state.disabledSubcategories[category]
    return disabled == null || !disabled.contains(subcategoryId)
  }

  fun setSubcategoryEnabled(category: SettingsCategory, subcategoryId: String, isEnabled: Boolean) {
    val disabledList = state.disabledSubcategories[category]
    if (isEnabled) {
      if (disabledList != null) {
        disabledList.remove(subcategoryId)
        if (disabledList.isEmpty()) {
          state.disabledSubcategories.remove(category)
        }
      }
    }
    else {
      if (disabledList == null) {
        val newList = ArrayList<String>()
        newList.add(subcategoryId)
        state.disabledSubcategories.put(category, newList)
      }
      else {
        if (!disabledList.contains(subcategoryId)) {
          disabledList.add(subcategoryId)
          Collections.sort(disabledList)
        }
      }
    }
  }

  class SettingsSyncSettingsState : BaseState() {
    var syncEnabled by property(false)

    var disabledCategories by list<SettingsCategory>()
    var disabledSubcategories by map<SettingsCategory, ArrayList<String>>()

    var migrationFromOldStorageChecked by property(false)

    @TestOnly
    internal fun reset() {
      syncEnabled = false
      disabledCategories = mutableListOf()
      disabledSubcategories = mutableMapOf()
      migrationFromOldStorageChecked = false
    }
  }
}