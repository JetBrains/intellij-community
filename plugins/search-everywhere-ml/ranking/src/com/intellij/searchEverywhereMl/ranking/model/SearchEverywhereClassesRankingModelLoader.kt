package com.intellij.searchEverywhereMl.ranking.model

import com.intellij.ide.actions.searcheverywhere.ml.SearchEverywhereTabWithMl
import com.intellij.internal.ml.DecisionFunction

internal class SearchEverywhereClassesRankingModelLoader : SearchEverywhereMLRankingModelLoader() {
  private val resourceDirectory = "classes_features"
  private val modelDirectory = "classes_model"
  private val expResourceDirectory = "classes_features_exp"
  private val expModelDirectory = "classes_model_exp"

  override val supportedTab = SearchEverywhereTabWithMl.CLASSES

  override fun getBundledModel(): DecisionFunction {
    return if (shouldProvideExperimentalModel())
      getCatBoostModel(expResourceDirectory, expModelDirectory)
    else
      getCatBoostModel(resourceDirectory, modelDirectory)
  }
}
