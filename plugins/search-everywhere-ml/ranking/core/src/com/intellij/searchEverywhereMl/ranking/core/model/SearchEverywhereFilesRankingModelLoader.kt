package com.intellij.searchEverywhereMl.ranking.core.model

import com.intellij.internal.ml.DecisionFunction
import com.intellij.searchEverywhereMl.SearchEverywhereTab

internal class SearchEverywhereFilesRankingModelLoader : SearchEverywhereMLRankingModelLoader() {
  private val resourceDirectory = "files_features"
  private val modelDirectory = "files_model"
  private val expResourceDirectory = "files_features_exp"
  private val expModelDirectory = "files_model_exp"

  override val supportedTab = SearchEverywhereTab.Files

  override fun getBundledModel(): DecisionFunction {
    return if (useExperimentalModel)
      getCatBoostModel(expResourceDirectory, expModelDirectory)
    else
      getCatBoostModel(resourceDirectory, modelDirectory)
  }
}
