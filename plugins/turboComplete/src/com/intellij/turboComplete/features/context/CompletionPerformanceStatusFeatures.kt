package com.intellij.turboComplete.features.context

import com.intellij.codeInsight.completion.ml.CompletionEnvironment
import com.intellij.codeInsight.completion.ml.ContextFeatureProvider
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.turboComplete.CompletionPerformanceParameters

class CompletionPerformanceStatusFeatures : ContextFeatureProvider {
  override fun getName() = "common_completion_kind"

  override fun calculateFeatures(environment: CompletionEnvironment): Map<String, MLFeatureValue> {
    val performanceParameters = CompletionPerformanceParameters.fromCompletionPreferences(environment.parameters)
    return mutableMapOf(
      "performance_enabled" to MLFeatureValue.binary(!performanceParameters.fixedGeneratorsOrder),
      "show_lookup_early" to MLFeatureValue.binary(performanceParameters.showLookupEarly),
    )
  }
}