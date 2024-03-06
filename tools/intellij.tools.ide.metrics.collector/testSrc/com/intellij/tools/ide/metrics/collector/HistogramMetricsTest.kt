package com.intellij.tools.ide.metrics.collector

import com.intellij.tools.ide.metrics.collector.meters.calculatePercentile
import io.kotest.matchers.shouldBe
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.metrics.data.AggregationTemporality
import io.opentelemetry.sdk.metrics.internal.data.ImmutableHistogramData
import io.opentelemetry.sdk.metrics.internal.data.ImmutableHistogramPointData
import org.junit.jupiter.api.Test

class HistogramMetricsTest {
  private fun createHistogramPointData(boundaries: List<Double>, counts: List<Long>): ImmutableHistogramPointData? {
    return ImmutableHistogramPointData.create(0, 0, Attributes.empty(), 100500.0,
                                              true, Double.NEGATIVE_INFINITY,
                                              true, Double.POSITIVE_INFINITY,
                                              boundaries, counts)
  }

  @Test
  fun `test percentile calculation with even number of elements`() {
    val boundaries = listOf(1.0, 2.0, 3.0, 4.0)
    val counts = listOf(1L, 1L, 1L, 1L)
    val histogramData = ImmutableHistogramData.create(
      AggregationTemporality.DELTA,
      listOf(createHistogramPointData(boundaries, counts))
    )
    histogramData.calculatePercentile(0.5) shouldBe 2.5
  }

  @Test
  fun `test percentile calculation with odd number of elements`() {
    val boundaries = listOf(1.0, 2.0, 3.0)
    val counts = listOf(1L, 1L, 1L)
    val histogramData = ImmutableHistogramData.create(
      AggregationTemporality.DELTA,
      listOf(createHistogramPointData(boundaries, counts)))
    histogramData.calculatePercentile(0.5) shouldBe 2.0
  }

  @Test
  fun `test percentile calculation with different counts`() {
    val boundaries = listOf(1.0, 2.0, 3.0)
    val counts = listOf(1L, 2L, 3L)
    val histogramData = ImmutableHistogramData.create(
      AggregationTemporality.DELTA,
      listOf(createHistogramPointData(boundaries, counts)))
    histogramData.calculatePercentile(0.5) shouldBe 3.0
  }

  @Test
  fun `test percentile calculation with zero percentile`() {
    val boundaries = listOf(1.0, 2.0, 3.0)
    val counts = listOf(1L, 1L, 1L)
    val histogramData = ImmutableHistogramData.create(
      AggregationTemporality.DELTA,
      listOf(createHistogramPointData(boundaries, counts)))
    histogramData.calculatePercentile(0.0) shouldBe 1.0
  }

  @Test
  fun `test percentile calculation with 100 percentile`() {
    val boundaries = listOf(1.0, 2.0, 3.0)
    val counts = listOf(1L, 1L, 1L)
    val histogramData = ImmutableHistogramData.create(
      AggregationTemporality.DELTA,
      listOf(createHistogramPointData(boundaries, counts)))
    histogramData.calculatePercentile(1.0) shouldBe 3.0
  }

  @Test
  fun `test percentile calculation with empty boundaries and counts`() {
    val boundaries = listOf<Double>()
    val counts = listOf<Long>()
    val histogramData = ImmutableHistogramData.create(
      AggregationTemporality.DELTA,
      listOf(createHistogramPointData(boundaries, counts)))
    try {
      histogramData.calculatePercentile(0.5)
    }
    catch (e: Exception) {
      e.message shouldBe "Cannot calculate percentile because collection is empty"
    }
  }

  @Test
  fun `test percentile calculation with negative percentile`() {
    val boundaries = listOf(1.0, 2.0, 3.0)
    val counts = listOf(1L, 1L, 1L)
    val histogramData = ImmutableHistogramData.create(
      AggregationTemporality.DELTA,
      listOf(createHistogramPointData(boundaries, counts)))
    try {
      histogramData.calculatePercentile(-0.1)
    }
    catch (e: Exception) {
      e.message shouldBe "Percentile must be between 0 and 1"
    }
  }

  @Test
  fun `test percentile calculation with percentile greater than 1`() {
    val boundaries = listOf(1.0, 2.0, 3.0)
    val counts = listOf(1L, 1L, 1L)
    val histogramData = ImmutableHistogramData.create(
      AggregationTemporality.DELTA,
      listOf(createHistogramPointData(boundaries, counts)))
    try {
      histogramData.calculatePercentile(1.1)
    }
    catch (e: Exception) {
      e.message shouldBe "Percentile must be between 0 and 1"
    }
  }

  @Test
  fun `test percentile calculation with boundaries and counts of different sizes`() {
    val boundaries = listOf(1.0, 2.0)
    val counts = listOf(1L, 1L, 1L)
    val histogramData = ImmutableHistogramData.create(
      AggregationTemporality.DELTA,
      listOf(createHistogramPointData(boundaries, counts))
    )
    try {
      histogramData.calculatePercentile(0.5)
    }
    catch (e: Exception) {
      e.message shouldBe "Boundaries and counts must be of the same size"
    }
  }

  @Test
  fun `test 95th percentile calculation`() {
    val boundaries = listOf(1.0, 2.0, 3.0, 4.0, 5.0)
    val counts = listOf(1L, 1L, 1L, 1L, 1L)
    val histogramData = ImmutableHistogramData.create(
      AggregationTemporality.DELTA,
      listOf(createHistogramPointData(boundaries, counts))
    )
    histogramData.calculatePercentile(0.95) shouldBe 5.0
  }

  @Test
  fun `test 100th percentile calculation`() {
    val boundaries = listOf(1.0, 2.0, 3.0, 4.0, 5.0)
    val counts = listOf(1L, 1L, 1L, 1L, 1L)
    val histogramData = ImmutableHistogramData.create(
      AggregationTemporality.DELTA,
      listOf(createHistogramPointData(boundaries, counts))
    )
    histogramData.calculatePercentile(1.0) shouldBe 5.0
  }
}