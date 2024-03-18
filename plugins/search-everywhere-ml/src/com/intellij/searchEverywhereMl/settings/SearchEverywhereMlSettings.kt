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
    if (tab == SearchEverywhereTabWithMlRanking.ALL) {
      return SearchEverywhereMlSettingsStorage.getInstance().enableMlRankingInAll
    } else if (tab == SearchEverywhereTabWithMlRanking.SYMBOLS) {
      return false
    }

    val settingsKey = getSettingsKey(tab)
    return AdvancedSettings.getBoolean(settingsKey)
  }

  fun isSortingByMlEnabledByDefault(tab: SearchEverywhereTabWithMlRanking): Boolean {
    if (tab == SearchEverywhereTabWithMlRanking.ALL) {
      return SearchEverywhereMlSettingsStorage.getInstance().enabledMlRankingInAllDefaultState
    } else if (tab == SearchEverywhereTabWithMlRanking.SYMBOLS) {
      return false
    }

    val settingsKey = getSettingsKey(tab)
    return AdvancedSettings.getDefaultBoolean(settingsKey)
  }

  private fun getSettingsKey(tab: SearchEverywhereTabWithMlRanking) = "searcheverywhere.ml.sort.${tab.name.lowercase()}"

  fun updateExperimentStateIfAllowed(tab: SearchEverywhereTabWithMlRanking, mlRankingEnabledByExperiment: Boolean): Boolean {
    if (tab == SearchEverywhereTabWithMlRanking.ALL) {
      // updating AdvancedSettings when experiment is enabled is only supported for All tab yet
      return SearchEverywhereMlSettingsStorage.getInstance().updateExperimentStateInAllTabIfAllowed(mlRankingEnabledByExperiment)
    }
    return true
  }

  fun disableExperiment(tab: SearchEverywhereTabWithMlRanking) {
    if (tab == SearchEverywhereTabWithMlRanking.ALL) {
      // updating AdvancedSettings when experiment is enabled is only supported for All tab yet
      SearchEverywhereMlSettingsStorage.getInstance().disableExperimentInAllTab()
    }
  }
}
