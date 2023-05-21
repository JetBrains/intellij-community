package com.intellij.searchEverywhereMl.ranking.model

import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereContributor
import com.intellij.internal.ml.DecisionFunction

internal class SearchEverywhereFilesRankingModelLoader : SearchEverywhereMLRankingModelLoader() {
  private val resourceDirectory = "files_features"
  private val modelDirectory = "files_model"
  private val expResourceDirectory = "files_features_exp"
  private val expModelDirectory = "files_model_exp"

  override val supportedContributorName : String = FileSearchEverywhereContributor::class.java.simpleName

  override fun getBundledModel(): DecisionFunction {
    return if (shouldProvideExperimentalModel())
      getCatBoostModel(expResourceDirectory, expModelDirectory)
    else
      getCatBoostModel(resourceDirectory, modelDirectory)
  }
}
