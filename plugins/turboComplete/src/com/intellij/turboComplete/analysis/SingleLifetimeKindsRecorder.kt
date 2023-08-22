package com.intellij.turboComplete.analysis

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.platform.ml.impl.turboComplete.CompletionKind
import com.intellij.platform.ml.impl.turboComplete.SuggestionGenerator
import com.intellij.platform.ml.impl.turboComplete.ranking.RankedKind
import kotlin.properties.Delegates

class SingleLifetimeKindsRecorder : PipelineListener {
  private var collectionStarted by Delegates.notNull<Long>()
  private var generatorCollected: MutableMap<CompletionKind, Long?> = mutableMapOf()
  private var kindRanked: MutableMap<CompletionKind, Long?> = mutableMapOf()
  private var generationStarted: MutableMap<CompletionKind, Long?> = mutableMapOf()
  private var generationFinished: MutableMap<CompletionKind, Long?> = mutableMapOf()
  private var firstExecutedKind: CompletionKind? = null

  override fun onInitialize(parameters: CompletionParameters) {}

  override fun onCollectionStarted() {
    collectionStarted = System.currentTimeMillis()
  }

  override fun onGeneratorCollected(suggestionGenerator: SuggestionGenerator) {
    generatorCollected[suggestionGenerator.kind] = System.currentTimeMillis()
  }

  override fun onRanked(ranked: List<RankedKind>) {
    val time = System.currentTimeMillis()
    ranked.forEach { kindRanked[it.kind] = time }
  }

  override fun onGenerationStarted(suggestionGenerator: SuggestionGenerator) {
    generationStarted[suggestionGenerator.kind] = System.currentTimeMillis()
    if (firstExecutedKind == null) {
      firstExecutedKind = suggestionGenerator.kind
    }
  }

  override fun onGenerationFinished(suggestionGenerator: SuggestionGenerator) {
    generationFinished[suggestionGenerator.kind] = System.currentTimeMillis()
  }

  fun captureCompletionKindLifetimeDurations(): Map<CompletionKind, GeneratorLifetimeDurations> {
    fun durationBetween(start: Long?, finish: Long?): Long? {
      return finish?.let { notNullFinish -> start?.let { notNullStart -> notNullFinish - notNullStart } }
    }
    return generatorCollected.keys.associateWith {
      GeneratorLifetimeDurations(
        created = durationBetween(collectionStarted, generatorCollected[it])!!,
        executed = durationBetween(generationStarted[it], generationFinished[it]),
        executedAsFirst = if (it == firstExecutedKind)
          durationBetween(generationStarted[it], generationFinished[it])
        else
          null
      )
    }
  }
}

data class GeneratorLifetimeDurations(
  val created: Long,
  val executed: Long?,
  val executedAsFirst: Long?
)