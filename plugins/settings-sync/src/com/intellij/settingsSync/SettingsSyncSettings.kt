package com.intellij.settingsSync

import com.intellij.openapi.components.*
import com.intellij.settingsSync.SettingsSyncSettings.Companion.COMPONENT_NAME
import com.intellij.settingsSync.SettingsSyncSettings.Companion.FILE_SPEC
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.xmlb.annotations.Property
import org.jetbrains.annotations.ApiStatus

@State(name = COMPONENT_NAME,
       category = SettingsCategory.SYSTEM,
       exportable = true,
       storages = [Storage(FILE_SPEC, usePathMacroManager = false)])
@ApiStatus.Internal
class SettingsSyncSettings : SettingsSyncState, SerializablePersistentStateComponent<SettingsSyncSettings.State>(State()) {
  companion object {
    @RequiresBlockingContext
    fun getInstance(): SettingsSyncSettings = service<SettingsSyncSettings>()

    const val FILE_SPEC = "settingsSync.xml"
    const val COMPONENT_NAME = "SettingsSyncSettings"
  }

  override var migrationFromOldStorageChecked: Boolean
    get() = state.migrationFromOldStorageChecked
    set(value) {
      updateState {
        it.withMigrationFromOldStorageChecked(value)
      }
    }

  override var syncEnabled
    get() = state.syncEnabled
    set(value) {
      updateState {
        it.withSyncEnabled(value)
      }
      fireSettingsStateChanged(value)
    }

  private fun fireSettingsStateChanged(syncEnabled: Boolean) {
    SettingsSyncEvents.getInstance().fireEnabledStateChanged(syncEnabled)
  }

  override fun isCategoryEnabled(category: SettingsCategory) = state.isCategoryEnabled(category)

  override fun setCategoryEnabled(category: SettingsCategory, isEnabled: Boolean) {
    updateState {
      it.withCategoryEnabled(category, isEnabled)
    }
  }

  override fun isSubcategoryEnabled(category: SettingsCategory, subcategoryId: String) = state.isSubcategoryEnabled(category, subcategoryId)
  override fun setSubcategoryEnabled(category: SettingsCategory, subcategoryId: String, isEnabled: Boolean) {
    updateState {
      it.withSubcategoryEnabled(category, subcategoryId, isEnabled)
    }
  }

  override val disabledCategories: List<SettingsCategory>
    get() = state.disabledCategories
  override val disabledSubcategories: Map<SettingsCategory, List<String>>
    get() = state.disabledSubcategories

  fun applyFromState(state: SettingsSyncState) {
    updateState {
      State(state.disabledCategories, state.disabledSubcategories, state.migrationFromOldStorageChecked, state.syncEnabled)
    }
  }

  data class State(@JvmField val disabledCategories: List<SettingsCategory> = emptyList(),
                   @JvmField val disabledSubcategories: Map<SettingsCategory, List<String>> = emptyMap(),
                   @JvmField @field:Property val migrationFromOldStorageChecked: Boolean = false,
                   @JvmField @field:Property val syncEnabled: Boolean = false) {
    fun withSyncEnabled(enabled: Boolean): State {
      return State(disabledCategories, disabledSubcategories, migrationFromOldStorageChecked, enabled)
    }

    fun withMigrationFromOldStorageChecked(checked: Boolean): State {
      return State(disabledCategories, disabledSubcategories, checked, syncEnabled)
    }

    private fun withDisabledCategories(newCategories: List<SettingsCategory>): State {
      return State(newCategories, disabledSubcategories, migrationFromOldStorageChecked, syncEnabled)
    }

    fun withCategoryEnabled(category: SettingsCategory, isEnabled: Boolean): State {
      val newCategories = ArrayList<SettingsCategory>(disabledCategories)
      if (isEnabled) {
        newCategories -= category
      }
      else {
        if (!newCategories.contains(category)) newCategories += category
      }
      newCategories.sort()
      return withDisabledCategories(newCategories)
    }


    private fun withDisabledSubcategories(newSubcategoriesMap: Map<SettingsCategory, List<String>>): State {
      return State(disabledCategories, newSubcategoriesMap, migrationFromOldStorageChecked, syncEnabled)
    }

    fun withSubcategoryEnabled(category: SettingsCategory, subcategoryId: String, isEnabled: Boolean): State {
      val newSubcategoriesMap = HashMap(disabledSubcategories)
      val subCategoryList = newSubcategoriesMap[category]
      if (isEnabled) {
        if (subCategoryList != null) {
          val newSubcategories = ArrayList(subCategoryList)
          newSubcategories -= subcategoryId
          if (newSubcategories.isEmpty()) {
            newSubcategoriesMap.remove(category)
          }
          else {
            newSubcategoriesMap[category] = newSubcategories
          }
        }
      }
      else {
        val newSubcategories = if (subCategoryList == null) {
          ArrayList()
        }
        else {
          ArrayList(subCategoryList)
        }
        if (!newSubcategories.contains(subcategoryId)) {
          newSubcategories += subcategoryId
        }
        newSubcategories.sort()
        newSubcategoriesMap[category] = newSubcategories
      }
      return withDisabledSubcategories(newSubcategoriesMap)
    }

    fun isCategoryEnabled(category: SettingsCategory) = !disabledCategories.contains(category)

    fun isSubcategoryEnabled(category: SettingsCategory, subcategoryId: String): Boolean {
      val disabled = disabledSubcategories[category]
      return disabled == null || !disabled.contains(subcategoryId)
    }
  }
}

interface SettingsSyncState {
  fun isCategoryEnabled(category: SettingsCategory): Boolean
  fun setCategoryEnabled(category: SettingsCategory, isEnabled: Boolean)
  fun isSubcategoryEnabled(category: SettingsCategory, subcategoryId: String): Boolean
  fun setSubcategoryEnabled(category: SettingsCategory, subcategoryId: String, isEnabled: Boolean)

  val disabledCategories: List<SettingsCategory>
  val disabledSubcategories: Map<SettingsCategory, List<String>>

  var syncEnabled: Boolean
  var migrationFromOldStorageChecked: Boolean
}

class SettingsSyncStateHolder(initState: SettingsSyncSettings.State = SettingsSyncSettings.State()) : SettingsSyncState {
  @Volatile
  private var state = initState
  override fun isCategoryEnabled(category: SettingsCategory) = state.isCategoryEnabled(category)

  override fun setCategoryEnabled(category: SettingsCategory, isEnabled: Boolean) {
    state = state.withCategoryEnabled(category, isEnabled)
  }

  override fun isSubcategoryEnabled(category: SettingsCategory, subcategoryId: String) = state.isSubcategoryEnabled(category, subcategoryId)

  override fun setSubcategoryEnabled(category: SettingsCategory, subcategoryId: String, isEnabled: Boolean) {
    state = state.withSubcategoryEnabled(category, subcategoryId, isEnabled)
  }

  override val disabledCategories: List<SettingsCategory>
    get() = state.disabledCategories
  override val disabledSubcategories: Map<SettingsCategory, List<String>>
    get() = state.disabledSubcategories
  override var syncEnabled: Boolean
    get() = state.syncEnabled
    set(value) {
      state = state.withSyncEnabled(value)
    }
  override var migrationFromOldStorageChecked: Boolean
    get() = state.migrationFromOldStorageChecked
    set(value) {
      state = state.withMigrationFromOldStorageChecked(value)
    }
}