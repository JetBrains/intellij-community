package com.intellij.tools.ide.metrics.collector

import com.intellij.tools.ide.metrics.collector.metrics.median
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test


class MedianCalculationTest {
  @Test
  fun medianValueCalculation() {
    val values = listOf(6L, 1L, 0L, 4L, 9L) // 0, 1, 4, 6, 9 => 4
    values.median().shouldBe(4)
  }
}