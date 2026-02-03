package com.intellij.searchEverywhereMl.ranking.core.model.local

import com.intellij.internal.ml.DecisionFunction
import com.intellij.openapi.util.registry.Registry
import com.intellij.searchEverywhereMl.SearchEverywhereTab


internal object LocalRankingModelProviderUtil {
  fun getLocalModel(tab: SearchEverywhereTab.TabWithMlRanking): DecisionFunction? {
    if (!isPathToLocalModelSpecified(tab)) return null

    val path = getPath(tab)

    val provider: LocalModelProvider = if (path.endsWith(".zip")) {
      LocalZipModelProvider()
    } else {
      LocalCatBoostModelProvider()
    }

    return provider.loadModel(path)
  }

  fun isPathToLocalModelSpecified(tab: SearchEverywhereTab.TabWithMlRanking) = Registry.get(tab.localModelPathRegistryKey).isChangedFromDefault()

  private fun getPath(tab: SearchEverywhereTab.TabWithMlRanking) = Registry.stringValue(tab.localModelPathRegistryKey)
}