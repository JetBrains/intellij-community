package com.intellij.searchEverywhereMl.ranking.model

import com.intellij.internal.ml.DecisionFunction
import com.intellij.searchEverywhereMl.SearchEverywhereTabWithMlRanking

internal class SearchEverywhereFilesRankingModelLoader : SearchEverywhereMLRankingModelLoader() {
  private val resourceDirectory = "files_features"
  private val modelDirectory = "files_model"
  private val expResourceDirectory = "files_features_exp"
  private val expModelDirectory = "files_model_exp"

  override val supportedTab = SearchEverywhereTabWithMlRanking.FILES

  override fun getBundledModel(): DecisionFunction {
    return if (shouldProvideExperimentalModel())
      getCatBoostModel(expResourceDirectory, expModelDirectory)
    else
      getCatBoostModel(resourceDirectory, modelDirectory)
  }
}
