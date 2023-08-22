package com.intellij.turboComplete.analysis.usage

import com.intellij.platform.ml.impl.turboComplete.CompletionKind

data class CombinedKindUsageStatistics(
  val created: Int,
  val generated: KindStatistics,
  val generatedInRow: KindStatistics,
  val recentGenerated: ValuePerPeriod<KindStatistics>,
) {
  val recentProbGenerateCorrect: ValuePerPeriod<Double>
    get() {
      return if (recentGenerated.period != 0)
        ValuePerPeriod(
          recentGenerated.period,
          recentGenerated.value.correct.toDouble() / recentGenerated.period
        )
      else {
        ValuePerPeriod(0, 0.0)
      }
    }
}

class CombinedKindUsageTracker(override val kind: CompletionKind,
                               recentWindowSize: Int,
                               maxUsageWindowSize: Int) : KindUsageTracker<CombinedKindUsageStatistics> {
  private val createdTracker = KindCreatedTracker(kind)
  private val generatedTracker = KindGenerationTracker(kind)
  private val generationInRawTracker = KindGenerationInRawTracker(kind, maxUsageWindowSize)
  private val recentGeneratedTracker = KindRecentUsageTracker(kind, recentWindowSize) {
    KindGenerationTracker(it)
  }
  private val allTrackers = listOf(
    createdTracker,
    generatedTracker,
    generationInRawTracker,
    recentGeneratedTracker,
  )

  override fun trackCreated() = allTrackers.forEach { it.trackCreated() }

  override fun trackGenerated(correct: Boolean) = allTrackers.forEach { it.trackGenerated(correct) }

  override fun getSummary(): CombinedKindUsageStatistics {
    return CombinedKindUsageStatistics(
      created = createdTracker.getSummary(),
      generated = generatedTracker.getSummary(),
      generatedInRow = generationInRawTracker.getSummary(),
      recentGenerated = recentGeneratedTracker.getSummary(),
    )
  }
}