package com.intellij.searchEverywhereMl.ranking.model

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID
import com.intellij.internal.ml.DecisionFunction

internal class SearchEverywhereAllRankingModelLoader : SearchEverywhereMLRankingModelLoader() {
  private val expResourceDirectory = "all_features_exp"
  private val expModelDirectory = "all_model_exp"

  override val supportedContributorName : String = ALL_CONTRIBUTORS_GROUP_ID

  override fun getBundledModel(): DecisionFunction {
    return getCatBoostModel(expResourceDirectory, expModelDirectory)
  }
}
