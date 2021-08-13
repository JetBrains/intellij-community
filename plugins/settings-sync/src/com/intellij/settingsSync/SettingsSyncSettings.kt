package com.intellij.settingsSync

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.util.EventDispatcher
import java.util.*

@State(name="SettingsSyncSettings", storages=[Storage("settingsSync.xml")])
internal class SettingsSyncSettings : SimplePersistentStateComponent<SettingsSyncSettings.SettingsSyncSettingsState>(SettingsSyncSettingsState()) {

  companion object {
    fun getInstance() = ApplicationManager.getApplication().getService(SettingsSyncSettings::class.java)
  }

  private val evenDispatcher = EventDispatcher.create(Listener::class.java)

  var syncEnabled
    get() = state.syncEnabled
    set(value) {
      state.syncEnabled = value
      evenDispatcher.multicaster.settingsChanged()
    }

  fun isCategoryEnabled(category: ComponentCategory) = !state.disabledCategories.contains(category)

  fun setCategoryEnabled(category: ComponentCategory, isEnabled: Boolean) {
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

  fun isSubcategoryEnabled(category: ComponentCategory, subcategoryId : String): Boolean {
    val disabled = state.disabledSubcategories[category]
    return disabled == null || !disabled.contains(subcategoryId)
  }

  fun setSubcategoryEnabled(category: ComponentCategory, subcategoryId: String, isEnabled : Boolean) {
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

    var disabledCategories by list<ComponentCategory>()
    var disabledSubcategories by map<ComponentCategory,ArrayList<String>>()
  }

  interface Listener : EventListener {
    fun settingsChanged()
  }

  fun addListener(listener: Listener, disposable: Disposable) {
    evenDispatcher.addListener(listener, disposable)
  }
}