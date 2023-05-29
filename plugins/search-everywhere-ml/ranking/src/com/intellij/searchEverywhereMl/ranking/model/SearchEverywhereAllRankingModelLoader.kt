package com.intellij.searchEverywhereMl.ranking.model

import com.intellij.ide.actions.searcheverywhere.ml.SearchEverywhereTabWithMl
import com.intellij.internal.ml.DecisionFunction

internal class SearchEverywhereAllRankingModelLoader : SearchEverywhereMLRankingModelLoader() {
  private val expResourceDirectory = "all_features_exp"
  private val expModelDirectory = "all_model_exp"

  override val supportedTab = SearchEverywhereTabWithMl.ALL

  override fun getBundledModel(): DecisionFunction {
    return getCatBoostModel(expResourceDirectory, expModelDirectory)
  }
}
