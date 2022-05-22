package com.intellij.ide.actions.searcheverywhere.ml.model.local

import com.intellij.ide.actions.searcheverywhere.ml.SearchEverywhereTabWithMl
import com.intellij.internal.ml.DecisionFunction
import com.intellij.openapi.util.registry.Registry


internal object LocalRankingModelProviderUtil {
  fun getLocalModel(contributorId: String): DecisionFunction? {
    val tab = SearchEverywhereTabWithMl.findById(contributorId) ?: return null
    if (!isPathToLocalModelSpecified(tab)) return null

    val path = getPath(tab)

    val provider: LocalModelProvider = if (path.endsWith(".zip")) {
      LocalZipModelProvider()
    } else {
      LocalCatBoostModelProvider()
    }

    return provider.loadModel(path)
  }

  fun isPathToLocalModelSpecified(tabId: String): Boolean {
    val tab = SearchEverywhereTabWithMl.findById(tabId) ?: return false
    return isPathToLocalModelSpecified(tab)
  }

  private fun isPathToLocalModelSpecified(tab: SearchEverywhereTabWithMl) = Registry.get(getRegistryKey(tab)).isChangedFromDefault

  private fun getRegistryKey(tab: SearchEverywhereTabWithMl) = "search.everywhere.ml.${tab.name.lowercase()}.model.path"

  private fun getPath(tab: SearchEverywhereTabWithMl) = Registry.stringValue(getRegistryKey(tab))
}