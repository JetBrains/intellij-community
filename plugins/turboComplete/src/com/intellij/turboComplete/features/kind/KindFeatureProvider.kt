package com.intellij.turboComplete.features.kind

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.ml.ContextFeatures
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.ml.impl.turboComplete.CompletionKind

interface KindFeatureProvider {
  fun getName(): String

  fun calculateFeatures(kind: CompletionKind,
                        location: CompletionLocation,
                        contextFeatures: ContextFeatures): Map<String, MLFeatureValue>

  companion object {
    val EP_NAME: ExtensionPointName<KindFeatureProvider> = ExtensionPointName.create(
      "com.intellij.turboComplete.features.kind.provider")
  }
}