package com.intellij.turboComplete

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.addingPolicy.PolicyController
import com.intellij.platform.ml.impl.turboComplete.SuggestionGeneratorExecutor
import com.intellij.platform.ml.impl.turboComplete.SuggestionGeneratorExecutorProvider
import com.intellij.turboComplete.analysis.DelegatingPipelineListener
import com.intellij.turboComplete.analysis.PipelineListener
import com.intellij.turboComplete.ranking.KindRelevanceSorter
import com.intellij.turboComplete.ranking.KindSorterProvider
import com.intellij.turboComplete.ranking.ReportingKindSorter

abstract class SortingExecutorProvider(
  private val executionPreferences: SortingExecutorPreferences
) : SuggestionGeneratorExecutorProvider {
  abstract val sorterProvider: KindSorterProvider
  override fun shouldBeCalled(parameters: CompletionParameters): Boolean {
    return sorterProvider.kindVariety.kindsCorrespondToParameters(parameters)
  }

  private fun createSorter(): KindRelevanceSorter {
    val rankingListeners: MutableList<PipelineListener> = PipelineListener.EP_NAME.extensionList.toMutableList()
    val delegatingListener = object : DelegatingPipelineListener {
      override val delegatedListeners: MutableList<PipelineListener>
        get() = rankingListeners
    }
    return ReportingKindSorter(delegatingListener, sorterProvider)
  }

  override fun createExecutor(
    parameters: CompletionParameters,
    policyController: PolicyController,
  ): SuggestionGeneratorExecutor {
    return SortingExecutor(createSorter(), parameters, policyController, executionPreferences)
  }
}