// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.turboComplete.platform.addingPolicy

import com.intellij.codeInsight.completion.addingPolicy.ElementsAddingPolicy
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.platform.ml.impl.turboComplete.SuggestionGenerator

class SuggestionGeneratorPuttingPolicy(
  base: ElementsAddingPolicy,
  private val suggestionGenerator: SuggestionGenerator,
) : ElementDecoratingPolicy(base) {
  override fun decorate(element: LookupElement) {
    element.putUserData(SuggestionGenerator.LOOKUP_ELEMENT_SUGGESTION_GENERATOR, suggestionGenerator)
  }
}

fun ElementsAddingPolicy.addingCompletionKind(suggestionGenerator: SuggestionGenerator): ElementsAddingPolicy {
  return SuggestionGeneratorPuttingPolicy(this, suggestionGenerator)
}