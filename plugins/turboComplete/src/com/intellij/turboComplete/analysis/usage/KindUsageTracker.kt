package com.intellij.turboComplete.analysis.usage

import com.intellij.platform.ml.impl.turboComplete.CompletionKind
import kotlin.math.min

interface KindUsageTracker<T> {
  val kind: CompletionKind

  fun trackCreated() {}

  fun trackGenerated(correct: Boolean) {}

  fun getSummary(): T
}

class KindCreatedTracker(override val kind: CompletionKind) : KindUsageTracker<Int> {
  private var created = 0

  override fun trackCreated() {
    created += 1
  }

  override fun getSummary() = created
}

data class KindStatistics(
  val correct: Int,
  val notCorrect: Int
)

class KindGenerationTracker(override val kind: CompletionKind) : KindUsageTracker<KindStatistics> {
  private var generatedCorrect = 0
  private var generatedNotCorrect = 0

  override fun trackGenerated(correct: Boolean) {
    if (correct)
      generatedCorrect += 1
    else
      generatedNotCorrect += 1
  }

  override fun getSummary() = KindStatistics(generatedCorrect, generatedNotCorrect)
}

class KindGenerationInRawTracker(override val kind: CompletionKind, private val maxPeriod: Int?) : KindUsageTracker<KindStatistics> {
  private var generatedCorrectInRow = 0
  private var generatedNotCorrectInRow = 0

  override fun trackGenerated(correct: Boolean) {
    if (correct) {
      generatedCorrectInRow += 1
      generatedNotCorrectInRow = 0
    }
    else {
      generatedCorrectInRow = 0
      generatedNotCorrectInRow += 1
    }
  }

  override fun getSummary(): KindStatistics {
    return maxPeriod?.let {
      KindStatistics(min(it, generatedCorrectInRow), min(it, generatedNotCorrectInRow))
    } ?: KindStatistics(generatedCorrectInRow, generatedNotCorrectInRow)
  }
}