package com.intellij.turboComplete.ranking

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.platform.ml.impl.turboComplete.CompletionKind
import com.intellij.platform.ml.impl.turboComplete.KindVariety
import com.intellij.platform.ml.impl.turboComplete.SuggestionGenerator
import com.intellij.platform.ml.impl.turboComplete.ranking.RankedKind

interface KindRelevanceSorter {
  val kindVariety: KindVariety

  fun sort(kinds: List<CompletionKind>, parameters: CompletionParameters): List<RankedKind>?

  fun sortGenerators(suggestionGenerators: List<SuggestionGenerator>, parameters: CompletionParameters): List<SuggestionGenerator>? {
    val completionKindNames = suggestionGenerators.map { it.kind }
    val order = sort(completionKindNames, parameters)
      ?.map { ranked ->
        val actualCompletionKind = suggestionGenerators.find { it.kind == ranked.kind }!!
        actualCompletionKind
      }
    return order
  }
}