package com.intellij.ide.actions.searcheverywhere.ml.settings

import com.intellij.ide.actions.searcheverywhere.ml.SearchEverywhereTabWithMl
import com.intellij.openapi.components.Service
import com.intellij.openapi.options.advanced.AdvancedSettings

@Service(Service.Level.APP)
internal class SearchEverywhereMlSettings {
  fun isSortingByMlEnabledInAnyTab(): Boolean {
    return SearchEverywhereTabWithMl.values().any {
      isSortingByMlEnabled(it)
    }
  }

  fun isSortingByMlEnabled(tab: SearchEverywhereTabWithMl): Boolean {
    val settingsKey = getSettingsKey(tab)
    return AdvancedSettings.getBoolean(settingsKey)
  }

  fun isSortingByMlEnabledByDefault(tab: SearchEverywhereTabWithMl): Boolean {
    val settingsKey = getSettingsKey(tab)
    return AdvancedSettings.getDefaultBoolean(settingsKey)
  }

  private fun getSettingsKey(tab: SearchEverywhereTabWithMl) = "searcheverywhere.ml.sort.${tab.name.lowercase()}"
}
