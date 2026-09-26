package com.intellij.searchEverywhereMl.ranking.core.model

import com.intellij.internal.ml.DecisionFunction
import com.intellij.searchEverywhereMl.SearchEverywhereTab

internal class SearchEverywhereAllRankingModelLoader : SearchEverywhereMLRankingModelLoader() {
  private val expResourceDirectory = "all_features_exp"
  private val expModelDirectory = "all_model_exp"

  override val supportedTab = SearchEverywhereTab.All

  override fun getBundledModel(): DecisionFunction {
    return getCatBoostModel(expResourceDirectory, expModelDirectory)
  }
}
