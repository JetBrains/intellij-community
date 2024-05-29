// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.searchEverywhereMl.ranking.core.model

import com.intellij.internal.ml.DecisionFunction
import com.intellij.internal.ml.FeatureMapper

internal abstract class SearchEverywhereRankingModel(protected val model: DecisionFunction) {
  abstract fun predict(features: Map<String, Any?>): Double
  protected fun rawModelPredict(features: Map<String, Any?>): Double {
    return model.predict(buildArray(model.featuresOrder, features))
  }

  protected fun buildArray(featuresOrder: Array<FeatureMapper>, features: Map<String, Any?>): DoubleArray {
    val array = DoubleArray(featuresOrder.size)
    for (i in featuresOrder.indices) {
      val mapper = featuresOrder[i]
      val value = features[mapper.featureName]
      array[i] = mapper.asArrayValue(value)
    }
    return array
  }
}

internal class SimpleSearchEverywhereRankingModel(model: DecisionFunction) : SearchEverywhereRankingModel(model) {
  override fun predict(features: Map<String, Any?>): Double = rawModelPredict(features)
}

internal class ExactMatchSearchEverywhereRankingModel(model: DecisionFunction) : SearchEverywhereRankingModel(model) {
  private val exactMatchKey = "prefixExact"
  private val extensionMatchKey = "fileTypeMatchesQuery"
  /**
   * Predict the preference adjusted for exact matches (mainly in Files and Classes tabs)
   * The value will be:
   *  - `> 0.99 for exact matches with a matching extension
   *  - 0.9-0.99 for exact match without the matching extension
   *  - `< 0.9 for non-exact matches
   *
   *  In these categories, the value is ordered by the ML model predicted value
   */
  override fun predict(features: Map<String, Any?>): Double {

    val isExactMatch = features.getOrDefault(exactMatchKey, false) == true
    val extensionMatch = features.getOrDefault(extensionMatchKey, false) == true
    val mlPrediction = model.predict(buildArray(model.featuresOrder, features))
    return if (isExactMatch) {
      if (extensionMatch) 0.99 + mlPrediction * 0.01 // Name + extension matches -> return preference is > 0.99
      else 0.9 + mlPrediction * 0.09 // Name matches but the extension doesn't -> return preference is 0.9-0.99.
    }
    else mlPrediction * 0.9 // No exact match -> return < 0.9
  }
}
