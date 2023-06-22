package com.intellij.searchEverywhereMl.settings

import com.intellij.openapi.components.Service
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.searchEverywhereMl.SearchEverywhereTabWithMlRanking

@Service(Service.Level.APP)
class SearchEverywhereMlSettings {
  fun isSortingByMlEnabledInAnyTab(): Boolean {
    return SearchEverywhereTabWithMlRanking.values().any {
      isSortingByMlEnabled(it)
    }
  }

  fun isSortingByMlEnabled(tab: SearchEverywhereTabWithMlRanking): Boolean {
    val settingsKey = getSettingsKey(tab)
    return AdvancedSettings.getBoolean(settingsKey)
  }

  fun isSortingByMlEnabledByDefault(tab: SearchEverywhereTabWithMlRanking): Boolean {
    val settingsKey = getSettingsKey(tab)
    return AdvancedSettings.getDefaultBoolean(settingsKey)
  }

  private fun getSettingsKey(tab: SearchEverywhereTabWithMlRanking) = "searcheverywhere.ml.sort.${tab.name.lowercase()}"
}
