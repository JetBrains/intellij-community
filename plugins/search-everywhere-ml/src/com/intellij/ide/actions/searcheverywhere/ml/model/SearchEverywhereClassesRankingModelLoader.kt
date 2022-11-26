package com.intellij.ide.actions.searcheverywhere.ml.model

import com.intellij.ide.actions.searcheverywhere.ClassSearchEverywhereContributor
import com.intellij.internal.ml.DecisionFunction

internal class SearchEverywhereClassesRankingModelLoader : SearchEverywhereMLRankingModelLoader() {
  private val expResourceDirectory = "classes_features_exp"
  private val expModelDirectory = "classes_model_exp"

  override val supportedContributor = ClassSearchEverywhereContributor::class.java

  override fun getBundledModel(): DecisionFunction {
    return getCatBoostModel(expResourceDirectory, expModelDirectory)
  }
}
