package com.intellij.turboComplete.platform.addingPolicy

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.addingPolicy.ElementsAddingPolicy
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.platform.ml.impl.turboComplete.CompletionKind
import com.intellij.platform.ml.impl.turboComplete.SuggestionGenerator
import com.intellij.platform.ml.impl.turboComplete.SuggestionGeneratorConsumer

class ConvertToCompletionKindPolicy(
  private val suggestionGeneratorConsumer: SuggestionGeneratorConsumer,
  private val convertedKind: CompletionKind,
  private val parameters: CompletionParameters
) : ElementsAddingPolicy.Default {
  private val buffer = mutableListOf<LookupElement>()

  override fun onResultStop(result: CompletionResultSet) {
    createCompletionKindFromBuffer(result)
  }

  override fun addElement(result: CompletionResultSet, element: LookupElement) {
    buffer.add(element)
  }

  override fun addAllElements(result: CompletionResultSet, elements: Iterable<LookupElement>) {
    buffer.addAll(elements)
  }

  override fun onDeactivate(result: CompletionResultSet) {
    createCompletionKindFromBuffer(result)
  }

  private fun createCompletionKindFromBuffer(result: CompletionResultSet) {
    if (buffer.isEmpty()) return

    val bufferCopy = mutableListOf<LookupElement>()
    bufferCopy.addAll(buffer)
    buffer.clear()
    suggestionGeneratorConsumer.pass(SuggestionGenerator.fromGenerator(
      convertedKind,
      parameters,
      result
    ) {
      result.addAllElements(bufferCopy)
    })
  }
}