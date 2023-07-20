package com.intellij.turboComplete.features.element

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.ml.ContextFeatures
import com.intellij.codeInsight.completion.ml.ElementFeatureProvider
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.platform.ml.impl.turboComplete.SuggestionGenerator.Companion.suggestionGenerator
import com.intellij.turboComplete.features.kind.FeaturesComputer

class ElementsKindFeatures : ElementFeatureProvider {
  override fun getName() = "completion_kind"

  override fun calculateFeatures(element: LookupElement,
                                 location: CompletionLocation,
                                 contextFeatures: ContextFeatures): Map<String, MLFeatureValue> {
    val suggestionGenerator = element.suggestionGenerator ?: return mutableMapOf()
    return FeaturesComputer.getKindFeatures(suggestionGenerator.kind, location, contextFeatures).toMutableMap()
  }
}