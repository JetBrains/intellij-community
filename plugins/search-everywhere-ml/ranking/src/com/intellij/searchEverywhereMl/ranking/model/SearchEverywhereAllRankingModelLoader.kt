package com.intellij.searchEverywhereMl.ranking.model

import com.intellij.internal.ml.DecisionFunction
import com.intellij.searchEverywhereMl.SearchEverywhereTabWithMlRanking

internal class SearchEverywhereAllRankingModelLoader : SearchEverywhereMLRankingModelLoader() {
  private val expResourceDirectory = "all_features_exp"
  private val expModelDirectory = "all_model_exp"

  override val supportedTab = SearchEverywhereTabWithMlRanking.ALL

  override fun getBundledModel(): DecisionFunction {
    return getCatBoostModel(expResourceDirectory, expModelDirectory)
  }
}
