package com.intellij.ide.actions.searcheverywhere.ml.model.local

import com.intellij.ide.actions.searcheverywhere.ml.model.SearchEverywhereMLRankingDecisionFunction
import com.intellij.internal.ml.DecisionFunction
import com.intellij.internal.ml.FeaturesInfo
import com.intellij.internal.ml.models.local.LocalCatBoostModelMetadataReader

internal class LocalCatBoostModelProvider: LocalModelProvider {
  private val featuresDirectory = "features"

  override fun loadModel(path: String): DecisionFunction {
    val metadataReader = LocalCatBoostModelMetadataReader(path, featuresDirectory)
    val metadata = FeaturesInfo.buildInfo(metadataReader)
    val model = metadataReader.loadModel()

    return object : SearchEverywhereMLRankingDecisionFunction(metadata) {
      override fun predict(features: DoubleArray): Double = model.makePredict(features)
    }
  }
}