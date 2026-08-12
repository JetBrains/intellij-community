package com.intellij.tools.ide.metrics.statistics

import kotlin.math.absoluteValue

data class BenchmarkVerdict(val direction: BenchmarkDirection, val severity: BenchmarkSeverity) : Comparable<BenchmarkVerdict> {
  companion object {
    val FASTER_SEVERE: BenchmarkVerdict = BenchmarkVerdict(BenchmarkDirection.FASTER, BenchmarkSeverity.SEVERE)
    val FASTER_MODERATE: BenchmarkVerdict = BenchmarkVerdict(BenchmarkDirection.FASTER, BenchmarkSeverity.MODERATE)
    val FASTER_SUSPICIOUS: BenchmarkVerdict = BenchmarkVerdict(BenchmarkDirection.FASTER, BenchmarkSeverity.SUSPICIOUS)

    val STEADY: BenchmarkVerdict = BenchmarkVerdict(BenchmarkDirection.NEUTRAL, BenchmarkSeverity.STEADY)

    val SLOWER_SUSPICIOUS: BenchmarkVerdict = BenchmarkVerdict(BenchmarkDirection.SLOWER, BenchmarkSeverity.SUSPICIOUS)
    val SLOWER_MODERATE: BenchmarkVerdict = BenchmarkVerdict(BenchmarkDirection.SLOWER, BenchmarkSeverity.MODERATE)
    val SLOWER_SEVERE: BenchmarkVerdict = BenchmarkVerdict(BenchmarkDirection.SLOWER, BenchmarkSeverity.SEVERE)

    fun maxOfOrSteady(verdicts: List<BenchmarkVerdict>): BenchmarkVerdict = BenchmarkVerdict(verdicts.maxOfOrNull { it.score } ?: STEADY.score)
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

  val score: Int = direction.sign * severity.score

  /**
   * We don't observe any changes in performance
   */
  fun isSteady(): Boolean = severity == BenchmarkSeverity.STEADY

  /**
   * We suspect that there are some changes in performance, but we are not sure.
   * We need more measurements to clarify the current state
   */
  fun isSuspicious(): Boolean = severity == BenchmarkSeverity.SUSPICIOUS

  /**
   * We are sure that there are some performance changes, but they may be considered as non-critical
   */
  fun isModerate(): Boolean = severity == BenchmarkSeverity.MODERATE

  /**
   * True if we have severe substantial changes in performance
   */
  fun isSevere(): Boolean = severity == BenchmarkSeverity.SEVERE

  /**
   * True if we are quite confident that the performance changes are real
   */
  fun isSubstantial(): Boolean = severity.isSubstantial()

  fun isDegradation(): Boolean = !isSteady() && direction.isDegradation()
  fun isAcceleration(): Boolean = !isSteady() && direction.isAcceleration()

  fun isSuspiciousDegradation(): Boolean = isSuspicious() && isDegradation()
  fun isSubstantialDegradation(): Boolean = isSubstantial() && isDegradation()
  fun isSevereDegradation(): Boolean = isSevere() && isDegradation()

  override fun equals(other: Any?): Boolean = if (other !is BenchmarkVerdict) false else other.score == this.score
  override fun compareTo(other: BenchmarkVerdict): Int = score.compareTo(other.score)
  override fun hashCode(): Int = score.hashCode()
  override fun toString(): String = "$direction/$severity"
}
