package com.intellij.turboComplete

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.addingPolicy.PolicyController
import com.intellij.platform.ml.impl.turboComplete.SuggestionGenerator
import com.intellij.platform.ml.impl.turboComplete.addingPolicy.PassDirectlyPolicy

class ImmediateExecutor(override val parameters: CompletionParameters,
                        override val policyController: PolicyController) : SuggestionGeneratorExecutor {
  override fun createNoneKindPolicy() = PassDirectlyPolicy()

  override fun executeAll() {}

  override fun pass(suggestionGenerator: SuggestionGenerator) {
    suggestionGenerator.generateCompletionVariants()
  }
}