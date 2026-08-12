package com.intellij.tools.ide.metrics.statistics

import kotlin.math.absoluteValue

data class BenchmarkVerdict(val direction: BenchmarkDirection, val severity: BenchmarkSeverity) : Comparable<BenchmarkVerdict> {
  companion object {
    val FASTER_SEVERE = BenchmarkVerdict(BenchmarkDirection.FASTER, BenchmarkSeverity.SEVERE)
    val FASTER_MODERATE = BenchmarkVerdict(BenchmarkDirection.FASTER, BenchmarkSeverity.MODERATE)
    val FASTER_SUSPICIOUS = BenchmarkVerdict(BenchmarkDirection.FASTER, BenchmarkSeverity.SUSPICIOUS)

    val STEADY = BenchmarkVerdict(BenchmarkDirection.NEUTRAL, BenchmarkSeverity.STEADY)

    val SLOWER_SUSPICIOUS = BenchmarkVerdict(BenchmarkDirection.SLOWER, BenchmarkSeverity.SUSPICIOUS)
    val SLOWER_MODERATE = BenchmarkVerdict(BenchmarkDirection.SLOWER, BenchmarkSeverity.MODERATE)
    val SLOWER_SEVERE = BenchmarkVerdict(BenchmarkDirection.SLOWER, BenchmarkSeverity.SEVERE)

    fun maxOfOrSteady(verdicts: List<BenchmarkVerdict>) = BenchmarkVerdict(verdicts.maxOfOrNull { it.score } ?: STEADY.score)
  }

  constructor(score: Int) : this(
    direction = when {
      score > 0 -> BenchmarkDirection.SLOWER
      score < 0 -> BenchmarkDirection.FASTER
      else -> BenchmarkDirection.NEUTRAL
    },
    severity = when (score.absoluteValue) {
      0 -> BenchmarkSeverity.STEADY
      1 -> BenchmarkSeverity.SUSPICIOUS
      2 -> BenchmarkSeverity.MODERATE
      else -> BenchmarkSeverity.SEVERE
    }

  )

  val score = direction.sign * severity.score

  /**
   * We don't observe any changes in performance
   */
  fun isSteady() = severity == BenchmarkSeverity.STEADY

  /**
   * We suspect that there are some changes in performance, but we are not sure.
   * We need more measurements to clarify the current state
   */
  fun isSuspicious() = severity == BenchmarkSeverity.SUSPICIOUS

  /**
   * We are sure that there are some performance changes, but they may be considered as non-critical
   */
  fun isModerate() = severity == BenchmarkSeverity.MODERATE

  /**
   * True if we have severe substantial changes in performance
   */
  fun isSevere() = severity == BenchmarkSeverity.SEVERE

  /**
   * True if we are quite confident that the performance changes are real
   */
  fun isSubstantial() = severity.isSubstantial()

  fun isDegradation() = !isSteady() && direction.isDegradation()
  fun isAcceleration() = !isSteady() && direction.isAcceleration()

  fun isSuspiciousDegradation() = isSuspicious() && isDegradation()
  fun isSubstantialDegradation() = isSubstantial() && isDegradation()
  fun isSevereDegradation() = isSevere() && isDegradation()

  override fun equals(other: Any?) = if (other !is BenchmarkVerdict) false else other.score == this.score
  override fun compareTo(other: BenchmarkVerdict) = score.compareTo(other.score)
  override fun hashCode() = score.hashCode()
  override fun toString() = "$direction/$severity"
}
