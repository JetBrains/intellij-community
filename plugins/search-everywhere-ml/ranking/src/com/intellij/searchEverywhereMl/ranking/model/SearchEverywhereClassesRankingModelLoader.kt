package com.intellij.searchEverywhereMl.ranking.model

import com.intellij.ide.actions.searcheverywhere.ClassSearchEverywhereContributor
import com.intellij.internal.ml.DecisionFunction

internal class SearchEverywhereClassesRankingModelLoader : SearchEverywhereMLRankingModelLoader() {
  private val resourceDirectory = "classes_features"
  private val modelDirectory = "classes_model"
  private val expResourceDirectory = "classes_features_exp"
  private val expModelDirectory = "classes_model_exp"


  override val supportedContributorName: String = ClassSearchEverywhereContributor::class.java.simpleName

  override fun getBundledModel(): DecisionFunction {
    return if (shouldProvideExperimentalModel())
      getCatBoostModel(expResourceDirectory, expModelDirectory)
    else
      getCatBoostModel(resourceDirectory, modelDirectory)
  }
}
