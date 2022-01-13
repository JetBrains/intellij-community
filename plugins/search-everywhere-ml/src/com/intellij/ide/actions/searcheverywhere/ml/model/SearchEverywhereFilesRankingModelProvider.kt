package com.intellij.ide.actions.searcheverywhere.ml.model

import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereContributor
import com.intellij.internal.ml.DecisionFunction

internal class SearchEverywhereFilesRankingModelProvider : SearchEverywhereMLRankingModelProvider() {
  private val resourceDirectory = "files_features"
  private val modelDirectory = "files_model"

  override val supportedContributor = FileSearchEverywhereContributor::class.java

  override fun getBundledModel(): DecisionFunction {
    return getCatBoostModel(resourceDirectory, modelDirectory)
  }
}
