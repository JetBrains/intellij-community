package com.intellij.turboComplete.platform

import com.intellij.codeInsight.completion.CompletionLookupOpener
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.platform.ml.impl.turboComplete.CompletionKind
import com.intellij.platform.ml.impl.turboComplete.SuggestionGenerator
import com.intellij.platform.ml.impl.turboComplete.ranking.RankedKind
import com.intellij.turboComplete.CompletionPerformanceParameters
import com.intellij.turboComplete.analysis.PipelineListener

class EarlyLookupOpener : PipelineListener {
  private val order: MutableList<CompletionKind> = mutableListOf()

  override fun onInitialize(parameters: CompletionParameters) {
  }

  override fun onRanked(ranked: List<RankedKind>) {
    order.addAll(ranked.map { it.kind })
  }

  override fun onGenerationFinished(suggestionGenerator: SuggestionGenerator) {
    val rankIndex = order.indexOf(suggestionGenerator.kind)
    if (rankIndex != 0) return
    showLookup(suggestionGenerator.parameters)
    order.clear()
  }

  companion object {
    fun showLookup(parameters: CompletionParameters) {
      val performanceParameters = CompletionPerformanceParameters.fromCompletionPreferences(parameters)
      if (!performanceParameters.showLookupEarly) return
      CompletionLookupOpener.showLookup(parameters)
    }
  }
}