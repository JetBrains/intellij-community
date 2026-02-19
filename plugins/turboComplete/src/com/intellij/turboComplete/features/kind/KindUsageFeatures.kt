package com.intellij.turboComplete.features.kind

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.ml.ContextFeatures
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.openapi.components.service
import com.intellij.platform.ml.impl.turboComplete.CompletionKind
import com.intellij.turboComplete.analysis.usage.KindVarietyUsageTracker

class KindUsageFeatures : KindFeatureProvider {
  override fun getName() = "usage"

  override fun calculateFeatures(kind: CompletionKind,
                                 location: CompletionLocation,
                                 contextFeatures: ContextFeatures): Map<String, MLFeatureValue> {
    val usageTracker = service<KindVarietyUsageTracker>()
    val usage = usageTracker.kindStatistics(kind)
    return mutableMapOf(
      "correct_prob" to MLFeatureValue.float(usage.recentProbGenerateCorrect.value),
      "correct_in_row" to MLFeatureValue.numerical(usage.generatedInRow.correct),
      "incorrect_in_row" to MLFeatureValue.numerical(usage.generatedInRow.notCorrect),
    )
  }
}