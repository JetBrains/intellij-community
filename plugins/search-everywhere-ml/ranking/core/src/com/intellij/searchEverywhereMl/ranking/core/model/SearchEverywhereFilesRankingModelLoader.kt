package com.intellij.searchEverywhereMl.ranking.core.model

import com.intellij.internal.ml.DecisionFunction
import com.intellij.searchEverywhereMl.SearchEverywhereTab

internal class SearchEverywhereFilesRankingModelLoader : SearchEverywhereMLRankingModelLoader() {
  private val resourceDirectory = "files_features"
  private val modelDirectory = "files_model"

  override val supportedTab = SearchEverywhereTab.Files

  override fun getBundledModel(): DecisionFunction = getCatBoostModel(resourceDirectory, modelDirectory)
}
