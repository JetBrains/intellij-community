// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.marketplaceMl.model

import com.intellij.internal.ml.DecisionFunction
import com.intellij.internal.ml.FeatureMapper
import com.intellij.internal.ml.ModelMetadata

abstract class MarketplaceRankingDecisionFunction(private val metadata: ModelMetadata) : DecisionFunction {
  override fun getFeaturesOrder(): Array<FeatureMapper> = metadata.featuresOrder

  override fun getRequiredFeatures(): List<String> = emptyList()

  override fun getUnknownFeatures(features: Collection<String>): List<String> = emptyList()

  override fun version(): String? = metadata.version
}