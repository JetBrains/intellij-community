package com.intellij.turboComplete

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.addingPolicy.ElementsAddingPolicy
import com.intellij.codeInsight.completion.addingPolicy.PolicyController
import com.intellij.platform.ml.impl.turboComplete.SuggestionGeneratorConsumer

interface SuggestionGeneratorExecutor : SuggestionGeneratorConsumer {
  val parameters: CompletionParameters
  val policyController: PolicyController

  fun createNoneKindPolicy(): ElementsAddingPolicy

  fun executeAll()
}