package com.intellij.turboComplete.features.kind

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.ml.ContextFeatures
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.platform.ml.impl.turboComplete.CompletionKind

class KindCommonFeatures : KindFeatureProvider {
  override fun getName() = "common"

  override fun calculateFeatures(kind: CompletionKind,
                                 location: CompletionLocation,
                                 contextFeatures: ContextFeatures): Map<String, MLFeatureValue> {
    return mutableMapOf(
      "is_name" to MLFeatureValue.categorical(kind.name)
    )
  }
}