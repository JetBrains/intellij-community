package com.intellij.turboComplete.analysis

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ml.impl.turboComplete.SuggestionGenerator
import com.intellij.platform.ml.impl.turboComplete.ranking.RankedKind

class PipelineDebugLogger : PipelineListener {
  override fun onInitialize(parameters: CompletionParameters) = log {
    "initialize pipeline: parameters=$parameters"
  }

  override fun onCollectionStarted() = log {
    "collection started"
  }

  override fun onGeneratorCollected(suggestionGenerator: SuggestionGenerator) = log {
    "generator collected: ${suggestionGenerator.printed()}"
  }

  override fun onCollectionFinished() = log {
    "collection finished"
  }

  override fun onGenerationStarted(suggestionGenerator: SuggestionGenerator) = log {
    "generation started: ${suggestionGenerator.printed()}"
  }

  override fun onGenerationFinished(suggestionGenerator: SuggestionGenerator) = log {
    "generation finished ${suggestionGenerator.printed()}"
  }

  override fun onRankingStarted() = log {
    "ranking started"
  }

  override fun onRanked(ranked: List<RankedKind>) = log {
    "ranked: ${ranked.map { "${it.kind.name}: ${it.relevance}" }}"
  }

  override fun onRankingFinished() = log {
    "ranking finished"
  }

  private fun log(message: () -> String) {
    if (Registry.`is` ("ml.completion.performance.logDebug")) {
      thisLogger().debug(message())
    }
  }

  private fun SuggestionGenerator.printed(): String {
    return "Generator{kind=${this.kind}, full=${this@printed}}"
  }
}