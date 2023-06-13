package com.intellij.turboComplete.analysis

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.platform.ml.impl.turboComplete.CompletionKind
import com.intellij.platform.ml.impl.turboComplete.SuggestionGenerator
import kotlin.properties.Delegates

class SinglePipelineRecorder : PipelineListener {
  private var collectionStarted by Delegates.notNull<Long>()
  private var collectionFinished by Delegates.notNull<Long>()
  private val generatorCollected: MutableMap<CompletionKind, Long> = mutableMapOf()
  private val generatorStarted: MutableMap<CompletionKind, Long> = mutableMapOf()
  private val generatorFinished: MutableMap<CompletionKind, Long> = mutableMapOf()
  private var rankingDurations: MutableList<Long> = mutableListOf()
  private var rankingStarted: Long? = null

  override fun onInitialize(parameters: CompletionParameters) {
  }

  override fun onCollectionStarted() {
    collectionStarted = System.currentTimeMillis()
  }

  override fun onGeneratorCollected(suggestionGenerator: SuggestionGenerator) {
    generatorCollected[suggestionGenerator.kind] = System.currentTimeMillis()
  }

  override fun onCollectionFinished() {
    collectionFinished = System.currentTimeMillis()
  }

  override fun onGenerationStarted(suggestionGenerator: SuggestionGenerator) {
    generatorStarted[suggestionGenerator.kind] = System.currentTimeMillis()
  }

  override fun onGenerationFinished(suggestionGenerator: SuggestionGenerator) {
    generatorFinished[suggestionGenerator.kind] = System.currentTimeMillis()
  }

  fun captureChronologyDurations(): PipelineChronologyDurations {
    return PipelineChronologyDurations(
      generation = collectionFinished - collectionStarted,
      ranking = rankingDurations.sum(),
    )
  }

  override fun onRankingStarted() {
    rankingStarted = System.currentTimeMillis()
  }

  override fun onRankingFinished() {
    rankingDurations.add(System.currentTimeMillis() - rankingStarted!!)
    rankingStarted = null
  }

  data class PipelineChronologyDurations(
    val generation: Long,
    val ranking: Long,
  )
}