package com.intellij.turboComplete.ranking

import com.intellij.completion.ml.ranker.local.MLCompletionLocalModelsLoader
import com.intellij.internal.ml.DecisionFunction
import com.intellij.platform.ml.impl.turboComplete.KindVariety

class LocalOrDefaultSorterProvider(
  private val languageId: String,
  modelRegistryKey: String,
  private val defaultDecisionFunctionProvider: () -> DecisionFunction,
  override val kindVariety: KindVariety,
) : KindSorterProvider {
  private val localModelLoader = MLCompletionLocalModelsLoader(modelRegistryKey)

  override fun createSorter(): KindRelevanceSorter {
    return MLKindSorter(loadLocalIfAny(languageId, defaultDecisionFunctionProvider), kindVariety)
  }

  private fun loadLocalIfAny(languageId: String, default: () -> DecisionFunction): DecisionFunction {
    return localModelLoader.getModel(languageId) ?: default()
  }
}