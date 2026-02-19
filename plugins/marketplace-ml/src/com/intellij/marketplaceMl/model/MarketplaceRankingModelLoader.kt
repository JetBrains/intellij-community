// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.marketplaceMl.model

import com.intellij.internal.ml.DecisionFunction
import com.intellij.internal.ml.FeaturesInfo
import kotlin.random.Random


class MarketplaceRankingModelLoader {
  companion object {
    fun loadModel(): DecisionFunction {
      val emptyMetadata = FeaturesInfo(emptySet(), emptyList(), emptyList(), emptyList(), emptyArray(), null)
      return object : MarketplaceRankingDecisionFunction(emptyMetadata) {
        override fun predict(features: DoubleArray?): Double = Random.nextDouble()
      }
    }
  }
}