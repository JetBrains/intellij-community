package com.intellij.tools.ide.metrics.collector

import com.intellij.tools.ide.metrics.collector.metrics.median
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test


class MedianCalculationTest {
  @Test
  fun medianValueCalculationForOddCollectionSize() {
    val values = listOf(6L, 1L, 0L, 4L, 9L) // 0, 1, 4, 6, 9 => 4
    values.median().shouldBe(4)
  }

  @Test
  fun medianValueCalculationForEvenCollectionSize() {
    val values = listOf(6L, 2L, 1L, 9L, 1L, 6L, 4L) // 1, 1, 2, 6, 6, 9 => 4
    values.median().shouldBe(4)
  }

  @Test
  fun medianValueCalculationForEvenCollectionSize2() {
    val values = listOf(10L, 30L) // 10, 30 => 20
    values.median().shouldBe(20)
  }

  @Test
  fun medianValueCalculationForEvenCollectionSizeWithRounding() {
    val values = listOf(1L, 4L) // 1, 4 => 2.5 => round to 2
    values.median().shouldBe(2)
  }
}