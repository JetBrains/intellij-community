package com.intellij.tools.ide.metrics.statistics

enum class BenchmarkDirection(val sign: Int) {
  FASTER(-1),
  NEUTRAL(0),
  SLOWER(1);

  companion object {
    fun create(sign: Int) = BenchmarkDirection.entries.firstOrNull { it.sign == sign }
                            ?: throw IllegalArgumentException("Invalid value for sign: $sign")
  }

  fun isAcceleration() = this == FASTER
  fun isDegradation() = this == SLOWER
}
