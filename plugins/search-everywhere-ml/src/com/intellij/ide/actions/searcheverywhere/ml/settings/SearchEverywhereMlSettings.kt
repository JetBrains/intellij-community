package com.intellij.ide.actions.searcheverywhere.ml.settings

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.openapi.components.Service
import com.intellij.openapi.options.advanced.AdvancedSettings

@Service(Service.Level.APP)
internal class SearchEverywhereMlSettings {
  fun isSortingByMlEnabledInAnyTab(): Boolean {
    return TabWithSettings.values().any {
      isSortingByMlEnabled(it)
    }
  }

  fun isSortingByMlEnabled(tabId: String): Boolean {
    val tab = TabWithSettings.findById(tabId) ?: return false
    return isSortingByMlEnabled(tab)
  }

  private fun isSortingByMlEnabled(tab: TabWithSettings): Boolean {
    val settingsKey = getSettingsKey(tab)
    return AdvancedSettings.getBoolean(settingsKey)
  }

  private fun getSettingsKey(tab: TabWithSettings) = "searcheverywhere.ml.sort.${tab.name.lowercase()}"

  private enum class TabWithSettings(val tabId: String) {
    // Define only tabs for which sorting by ML can be turned or off in the advanced settings
    ACTIONS(ActionSearchEverywhereContributor::class.java.simpleName);

    companion object {
      fun findById(tabId: String) = values().find { it.tabId == tabId }
    }
  }
}