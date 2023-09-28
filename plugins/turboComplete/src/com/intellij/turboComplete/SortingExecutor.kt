package com.intellij.turboComplete

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.addingPolicy.ElementsAddingPolicy
import com.intellij.codeInsight.completion.addingPolicy.PassDirectlyPolicy
import com.intellij.codeInsight.completion.addingPolicy.PolicyController
import com.intellij.openapi.components.service
import com.intellij.platform.ml.impl.turboComplete.CompletionKind
import com.intellij.platform.ml.impl.turboComplete.SuggestionGenerator
import com.intellij.platform.ml.impl.turboComplete.SuggestionGeneratorExecutor
import com.intellij.turboComplete.analysis.usage.KindVarietyUsageTracker
import com.intellij.turboComplete.platform.addingPolicy.BufferingPolicy
import com.intellij.turboComplete.platform.addingPolicy.ConvertToCompletionKindPolicy
import com.intellij.turboComplete.platform.addingPolicy.addingActualContributor
import com.intellij.turboComplete.platform.addingPolicy.addingCompletionKind
import com.intellij.turboComplete.ranking.KindRelevanceSorter

class SortingExecutor(
  private val sorter: KindRelevanceSorter,
  override val parameters: CompletionParameters,
  override val policyController: PolicyController,
  private val executionPreferences: SortingExecutorPreferences
) : SuggestionGeneratorExecutor {
  private val executedGenerators: MutableSet<SuggestionGenerator> = mutableSetOf()
  private val nonExecutedGenerators: MutableSet<SuggestionGenerator> = mutableSetOf()
  private val mostRelevantKinds: MutableList<CompletionKind> = run {
    val usageTracker = service<KindVarietyUsageTracker>()
    val onceGeneratedKinds = usageTracker.trackedKinds(sorter.kindVariety)
    sorter.sort(onceGeneratedKinds, parameters)
      ?.map { it.kind }
      ?.take(executionPreferences.executeMostRelevantWhenPassed)
      ?.toMutableList()
    ?: mutableListOf()
  }

  override fun createNoneKindPolicy() = when (executionPreferences.policyForNoneKind) {
    SortingExecutorPreferences.NoneKindPolicy.BUFFER -> BufferingPolicy()

    SortingExecutorPreferences.NoneKindPolicy.PASS_TO_RESULT -> PassDirectlyPolicy()

    SortingExecutorPreferences.NoneKindPolicy.CREATE_NONE_KIND -> ConvertToCompletionKindPolicy(
      this,
      CompletionKind(NullableKindName.NONE_KIND, sorter.kindVariety),
      parameters
    )
  }

  private fun makeMostRelevantKindPolicy() = when (executionPreferences.policyForMostRelevant) {
    SortingExecutorPreferences.MostRelevantKindPolicy.BUFFER -> BufferingPolicy()
    SortingExecutorPreferences.MostRelevantKindPolicy.PASS_TO_RESULT -> PassDirectlyPolicy()
  }

  private fun makeKindPolicy(suggestionGenerator: SuggestionGenerator): ElementsAddingPolicy {
    val plainPolicy =
      if (executedGenerators.isEmpty()) makeMostRelevantKindPolicy()
      else PassDirectlyPolicy()

    return plainPolicy
      .addingActualContributor(suggestionGenerator)
      .addingCompletionKind(suggestionGenerator)
  }

  private fun invokeCompletionKind(suggestionGenerator: SuggestionGenerator) {
    val policy = makeKindPolicy(suggestionGenerator)
    policyController.invokeWithPolicy(policy) {
      suggestionGenerator.generateCompletionVariants()
    }
    executedGenerators.add(suggestionGenerator)
    nonExecutedGenerators.remove(suggestionGenerator)
  }

  override fun executeAll() {
    if (nonExecutedGenerators.isEmpty()) return
    val executionOrder = sorter.sortGenerators(nonExecutedGenerators.toList(), parameters)
    executionOrder?.forEach {
      invokeCompletionKind(it)
    } ?: run {
      nonExecutedGenerators.forEach { invokeCompletionKind(it) }
    }
  }

  override fun pass(suggestionGenerator: SuggestionGenerator) {
    nonExecutedGenerators.add(suggestionGenerator)
    if (mostRelevantKinds.getOrNull(0) == suggestionGenerator.kind) {
      invokeCompletionKind(suggestionGenerator)
      mostRelevantKinds.removeFirst()
    }
  }
}