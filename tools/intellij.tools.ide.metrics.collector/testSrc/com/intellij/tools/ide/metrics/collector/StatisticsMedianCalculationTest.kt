package com.intellij.tools.ide.metrics.collector

import com.intellij.tools.ide.metrics.collector.metrics.median
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test


class StatisticsMedianCalculationTest {
  @Test
  fun medianValueCalculationForOddCollectionSize() {
    val values = listOf(6, 1, 0, 4, 9) // 0, 1, 4, 6, 9 => 4
    values.median().shouldBe(4)
  }

  @Test
  fun medianValueCalculationForEvenCollectionSize() {
    val values = listOf(6, 2, 1, 9, 1, 6, 4) // 1, 1, 2, 6, 6, 9 => 4
    values.median().shouldBe(4)
  }

  @Test
  fun medianValueCalculationForEvenCollectionSize2() {
    val values = listOf(10, 30) // 10, 30 => 20
    values.median().shouldBe(20)
  }

  @Test
  fun medianValueCalculationForEvenCollectionSizeWithRounding() {
    val values = listOf(1, 4) // 1, 4 => 2.5 => round to 2
    values.median().shouldBe(2)
  }
}