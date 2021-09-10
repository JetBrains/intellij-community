package com.intellij.ide.actions.searcheverywhere.ml.settings

import com.intellij.ide.actions.searcheverywhere.ml.SearchEverywhereTabWithMl
import com.intellij.openapi.components.Service
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.options.advanced.AdvancedSettingsImpl

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
    // TODO: Clean up - expose default value from AdvancedSettings
    val settingsKey = getSettingsKey(tab)
    val currentValue = AdvancedSettings.getBoolean(settingsKey)
    val settings = AdvancedSettings.getInstance() as AdvancedSettingsImpl
    val isNonDefault = settings.isNonDefault(settingsKey)

    return if (isNonDefault) !currentValue else currentValue
  }

  private fun getSettingsKey(tab: SearchEverywhereTabWithMl) = "searcheverywhere.ml.sort.${tab.name.lowercase()}"
}
