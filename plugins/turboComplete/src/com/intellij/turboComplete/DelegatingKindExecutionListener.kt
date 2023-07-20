package com.intellij.turboComplete

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.platform.ml.impl.turboComplete.KindExecutionListener
import com.intellij.platform.ml.impl.turboComplete.SuggestionGenerator

interface DelegatingKindExecutionListener<T : KindExecutionListener> : KindExecutionListener {
  val delegatedListeners: MutableList<T>

  override fun onInitialize(parameters: CompletionParameters) = delegatedListeners.forEach { it.onInitialize(parameters) }

  override fun onCollectionStarted() = delegatedListeners.forEach { it.onCollectionStarted() }

  override fun onGeneratorCollected(suggestionGenerator: SuggestionGenerator) = delegatedListeners.forEach {
    it.onGeneratorCollected(suggestionGenerator)
  }

  override fun onGenerationStarted(suggestionGenerator: SuggestionGenerator) = delegatedListeners.forEach {
    it.onGenerationStarted(suggestionGenerator)
  }

  override fun onGenerationFinished(suggestionGenerator: SuggestionGenerator) = delegatedListeners.forEach {
    it.onGenerationFinished(suggestionGenerator)
  }

  override fun onCollectionFinished() = delegatedListeners.forEach { it.onCollectionFinished() }
}