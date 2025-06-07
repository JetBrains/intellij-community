package com.intellij.searchEverywhereMl.ranking.core.model.local

import com.intellij.internal.ml.DecisionFunction
import com.intellij.searchEverywhereMl.ranking.core.model.CatBoostModelFactory

internal class LocalCatBoostModelProvider: LocalModelProvider {
  private val featuresDirectory = "features"

  override fun loadModel(path: String): DecisionFunction {
    return CatBoostModelFactory()
      .withModelDirectory(path)
      .withResourceDirectory(featuresDirectory)
      .buildLocalModel()
  }
}