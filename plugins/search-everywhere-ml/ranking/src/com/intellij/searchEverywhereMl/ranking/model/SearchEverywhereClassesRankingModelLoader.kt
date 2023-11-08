package com.intellij.searchEverywhereMl.ranking.model

import com.intellij.internal.ml.DecisionFunction
import com.intellij.internal.ml.FeaturesInfo
import com.intellij.internal.ml.ResourcesModelMetadataReader
import com.intellij.searchEverywhere.model.classes.exp.PredictionModel
import com.intellij.searchEverywhereMl.SearchEverywhereTabWithMlRanking

internal class SearchEverywhereClassesRankingModelLoader : SearchEverywhereMLRankingModelLoader() {
  private val resourceDirectory = "classes_features"
  private val modelDirectory = "classes_model"
  private val expResourceDirectory = "classes_features_exp"

  override val supportedTab = SearchEverywhereTabWithMlRanking.CLASSES

  override fun getBundledModel(): DecisionFunction {
    return if (shouldProvideExperimentalModel())
      getExperimentalModel()
    else
      getCatBoostModel(resourceDirectory, modelDirectory)
  }

  private fun getExperimentalModel(): SearchEverywhereMLRankingDecisionFunction {
    val metadata = FeaturesInfo.buildInfo(ResourcesModelMetadataReader(this::class.java, expResourceDirectory))
    return object : SearchEverywhereMLRankingDecisionFunction(metadata) {
      override fun predict(features: DoubleArray?): Double = PredictionModel.makePredict(features)
    }
  }
}
