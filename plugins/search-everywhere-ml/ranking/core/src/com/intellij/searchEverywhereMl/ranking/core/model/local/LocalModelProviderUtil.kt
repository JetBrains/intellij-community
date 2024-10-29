package com.intellij.searchEverywhereMl.ranking.core.model.local

import com.intellij.internal.ml.DecisionFunction
import com.intellij.openapi.util.registry.Registry
import com.intellij.searchEverywhereMl.SearchEverywhereTabWithMlRanking


internal object LocalRankingModelProviderUtil {
  fun getLocalModel(tab: SearchEverywhereTabWithMlRanking): DecisionFunction? {
    if (!isPathToLocalModelSpecified(tab)) return null

    val path = getPath(tab)

    val provider: LocalModelProvider = if (path.endsWith(".zip")) {
      LocalZipModelProvider()
    } else {
      LocalCatBoostModelProvider()
    }

    return provider.loadModel(path)
  }

  fun isPathToLocalModelSpecified(tab: SearchEverywhereTabWithMlRanking) = Registry.get(getRegistryKey(tab)).isChangedFromDefault()

  private fun getRegistryKey(tab: SearchEverywhereTabWithMlRanking) = "search.everywhere.ml.${tab.name.lowercase()}.model.path"

  private fun getPath(tab: SearchEverywhereTabWithMlRanking) = Registry.stringValue(getRegistryKey(tab))
}