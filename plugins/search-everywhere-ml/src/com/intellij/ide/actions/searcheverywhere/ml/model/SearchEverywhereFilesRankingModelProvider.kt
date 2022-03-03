package com.intellij.ide.actions.searcheverywhere.ml.model

import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereContributor
import com.intellij.internal.ml.DecisionFunction
import com.intellij.internal.ml.FeaturesInfo
import com.intellij.internal.ml.ResourcesModelMetadataReader
import com.intellij.searchEverywhere.model.files.PredictionModel

internal class SearchEverywhereFilesRankingModelProvider : SearchEverywhereMLRankingModelProvider() {
  private val resourceDirectory = "file-features"

  override val supportedContributor = FileSearchEverywhereContributor::class.java

  override fun getBundledModel(): DecisionFunction {
    val metadata = FeaturesInfo.buildInfo(ResourcesModelMetadataReader(this::class.java, resourceDirectory))
    return object : SearchEverywhereMLRankingDecisionFunction(metadata) {
      override fun predict(features: DoubleArray?): Double = PredictionModel.makePredict(features)
    }
  }
}
