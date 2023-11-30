package com.intellij.turboComplete.features.context

import com.intellij.codeInsight.completion.ml.CompletionEnvironment
import com.intellij.codeInsight.completion.ml.ContextFeatureProvider
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.openapi.components.service
import com.intellij.turboComplete.analysis.usage.KindVarietyUsageTracker

class AllKindsUsageFeatures : ContextFeatureProvider {
  override fun getName() = "kind_usage"

  override fun calculateFeatures(environment: CompletionEnvironment): Map<String, MLFeatureValue> {
    val usageTracker = service<KindVarietyUsageTracker>()
    val features = mutableMapOf<String, MLFeatureValue>()

    val trackedKinds = usageTracker.trackedKinds(environment.parameters)

    features.putAll(
      trackedKinds
        .map { kind -> kind to usageTracker.kindStatistics(kind) }
        .filter { it.second.generated.correct > 0 }
        .flatMap { (kind, usage) ->
          listOf(
            "correct_prob_${kind.name}" to MLFeatureValue.float(usage.recentProbGenerateCorrect.value),
            "correct_in_row_${kind.name}" to MLFeatureValue.numerical(usage.generatedInRow.correct),
            "incorrect_in_row_${kind.name}" to MLFeatureValue.numerical(usage.generatedInRow.notCorrect),
          )
        }
    )
    return features
  }
}