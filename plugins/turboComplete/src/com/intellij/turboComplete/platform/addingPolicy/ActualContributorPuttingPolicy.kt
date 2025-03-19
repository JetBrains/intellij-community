package com.intellij.turboComplete.platform.addingPolicy

import com.intellij.codeInsight.completion.addingPolicy.ElementsAddingPolicy
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.completion.ml.common.LOOKUP_ORIGINAL_ELEMENT_CONTRIBUTOR_TYPE
import com.intellij.platform.ml.impl.turboComplete.SuggestionGenerator

class ActualContributorPuttingPolicy(
  base: ElementsAddingPolicy,
  private val actualCompletionContributorClass: Class<*>,
) : ElementDecoratingPolicy(base) {
  override fun decorate(element: LookupElement) {
    element.putUserData(LOOKUP_ORIGINAL_ELEMENT_CONTRIBUTOR_TYPE,
                        actualCompletionContributorClass)
  }
}

fun ElementsAddingPolicy.addingActualContributor(suggestionGenerator: SuggestionGenerator): ActualContributorPuttingPolicy {
  return ActualContributorPuttingPolicy(this, suggestionGenerator.kind.variety.actualCompletionContributorClass)
}