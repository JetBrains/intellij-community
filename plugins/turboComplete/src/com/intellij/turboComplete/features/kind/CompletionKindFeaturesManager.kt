package com.intellij.turboComplete.features.kind

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.ml.ContextFeatures
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.util.Key
import com.intellij.platform.ml.impl.turboComplete.CompletionKind

internal class CompletionKindFeaturesManager {
  private val computedFeatures: MutableMap<CompletionKind, Map<String, MLFeatureValue>> = mutableMapOf()

  private fun computeKindFeatures(kind: CompletionKind,
                                  location: CompletionLocation,
                                  contextFeatures: ContextFeatures): Map<String, MLFeatureValue> {
    return KindFeatureProvider.EP_NAME.extensionList.flatMap { provider ->
      provider.calculateFeatures(kind, location, contextFeatures).entries.map { (featureName, featureValue) ->
        "${provider.getName()}_$featureName" to featureValue
      }
    }.toMap()
  }

  fun getOrCompute(kind: CompletionKind,
                   location: CompletionLocation,
                   contextFeatures: ContextFeatures): Map<String, MLFeatureValue> {
    return computedFeatures.getOrPut(kind) {
      computeKindFeatures(kind, location, contextFeatures)
    }
  }

  companion object {
    private val LOOKUP_COMPLETION_KIND_FEATURES_MANAGER = Key<CompletionKindFeaturesManager>("Manager of CompletionKinds' features")

    val LookupImpl.completionKindFeaturesManager: CompletionKindFeaturesManager
      get() = this.getUserData(LOOKUP_COMPLETION_KIND_FEATURES_MANAGER) ?: run {
        val manager = CompletionKindFeaturesManager()
        this.putUserData(LOOKUP_COMPLETION_KIND_FEATURES_MANAGER, manager)
        manager
      }
  }
}