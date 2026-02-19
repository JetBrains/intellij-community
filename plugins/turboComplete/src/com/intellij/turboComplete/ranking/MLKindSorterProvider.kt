package com.intellij.turboComplete.ranking

import com.intellij.internal.ml.DecisionFunction
import com.intellij.platform.ml.impl.turboComplete.KindVariety

class MLKindSorterProvider(
  override val kindVariety: KindVariety,
  val decisionFunctionProvider: () -> DecisionFunction,
) : KindSorterProvider {
  override fun createSorter(): KindRelevanceSorter {
    return MLKindSorter(decisionFunctionProvider(), kindVariety)
  }
}

fun MLKindSorterProvider.provideLocalIfAny(languageId: String, registryKey: String): KindSorterProvider {
  return LocalOrDefaultSorterProvider(languageId, registryKey, this.decisionFunctionProvider, this.kindVariety)
}