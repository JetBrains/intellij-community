package com.intellij.marketplaceMl.model

import com.intellij.internal.ml.DecisionFunction
import com.intellij.internal.ml.FeatureMapper

class MarketplaceRankingModel {
  private val decisionFunction: DecisionFunction = MarketplaceRankingModelLoader.loadModel()

  fun predictScore(featureMap: Map<String, Any?>): Double {
    return decisionFunction.predict(buildArray(decisionFunction.featuresOrder, featureMap))
  }

  private fun buildArray(featuresOrder: Array<FeatureMapper>, featureMap: Map<String, Any?>): DoubleArray {
    return DoubleArray(featuresOrder.size) {
      val mapper = featuresOrder[it]
      mapper.asArrayValue(featureMap[mapper.featureName])
    }
  }
}