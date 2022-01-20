// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml.model

import com.intellij.internal.ml.FeatureMapper


internal class SearchEverywhereRankingModel(private val loader: SearchEverywhereMLRankingModelLoader) {
  val model by lazy { loader.loadModel() }

  fun predict(features: Map<String, Any>): Double {
    return model.predict(buildArray(model.featuresOrder, features))
  }

  private fun buildArray(featuresOrder: Array<FeatureMapper>, features: Map<String, Any>): DoubleArray {
    val array = DoubleArray(featuresOrder.size)
    for (i in featuresOrder.indices) {
      val mapper = featuresOrder[i]
      val value = features[mapper.featureName]
      array[i] = mapper.asArrayValue(value)
    }
    return array
  }
}
