package com.intellij.turboComplete.features.kind

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.ml.ContextFeatures
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.platform.ml.impl.turboComplete.CompletionKind
import com.intellij.turboComplete.features.kind.CompletionKindFeaturesManager.Companion.completionKindFeaturesManager

object FeaturesComputer {
  fun getKindFeatures(kind: CompletionKind, location: CompletionLocation, contextFeatures: ContextFeatures): Map<String, MLFeatureValue> {
    val lookup = LookupManager.getActiveLookup(location.completionParameters.editor) as? LookupImpl ?: return emptyMap()
    val featuresManager = lookup.completionKindFeaturesManager
    return featuresManager.getOrCompute(kind, location, contextFeatures)
  }
}