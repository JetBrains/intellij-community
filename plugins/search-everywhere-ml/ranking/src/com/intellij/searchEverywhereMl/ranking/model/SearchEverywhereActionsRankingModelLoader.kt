// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.searchEverywhereMl.ranking.model

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.internal.ml.DecisionFunction
import com.intellij.internal.ml.FeaturesInfo
import com.intellij.internal.ml.ResourcesModelMetadataReader
import com.intellij.searchEverywhere.model.actions.PredictionModel

internal class SearchEverywhereActionsRankingModelLoader : SearchEverywhereMLRankingModelLoader() {
  private val standardResourceDirectory = "actions_features"

  private val expResourceDirectory = "actions_features_exp"
  private val expModelDirectory = "actions_model_exp"

  override val supportedContributorName: String = ActionSearchEverywhereContributor::class.java.simpleName

  override fun getBundledModel(): DecisionFunction {
    return if (shouldProvideExperimentalModel()) {
      getExperimentalModel()
    }
    else {
      getStandardModel()
    }
  }

  private fun getStandardModel(): SearchEverywhereMLRankingDecisionFunction {
    val metadata = FeaturesInfo.buildInfo(ResourcesModelMetadataReader(this::class.java, standardResourceDirectory))
    return object : SearchEverywhereMLRankingDecisionFunction(metadata) {
      override fun predict(features: DoubleArray?): Double = PredictionModel.makePredict(features)
    }
  }

  private fun getExperimentalModel(): DecisionFunction {
    return getCatBoostModel(expResourceDirectory, expModelDirectory)
  }
}
