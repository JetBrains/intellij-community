package com.intellij.tools.ide.metrics.statistics

enum class BenchmarkSeverity(val score: Int) {
  /**
   * We don't observe any changes in performance
   */
  STEADY(0),

  /**
   * We suspect that there are some changes in performance, but we are not sure.
   * We need more measurements to clarify the current state
   */
  SUSPICIOUS(1),

  /**
   * We are sure that there are some performance changes, but they may be considered as non-severe
   */
  MODERATE(2),

  /**
   * We have severe changes in performance
   */
  SEVERE(3);

  companion object {
    fun create(score: Int): BenchmarkSeverity = entries.firstOrNull { it.score == score }
                                                                                          ?: throw IllegalArgumentException("Invalid value for score: $score")
  }

  /**
   * True if we are quite confident that the performance changes are real
   */
  fun isSubstantial(): Boolean = this.score >= MODERATE.score
}
