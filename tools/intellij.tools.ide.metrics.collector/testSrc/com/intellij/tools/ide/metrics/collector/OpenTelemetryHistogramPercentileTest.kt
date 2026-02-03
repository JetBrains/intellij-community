package com.intellij.tools.ide.metrics.collector

import com.intellij.tools.ide.metrics.collector.metrics.calculatePercentile
import io.kotest.matchers.shouldBe
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.metrics.data.AggregationTemporality
import io.opentelemetry.sdk.metrics.internal.data.ImmutableHistogramData
import io.opentelemetry.sdk.metrics.internal.data.ImmutableHistogramPointData
import org.junit.jupiter.api.Test

internal fun createHistogramData(boundaries: List<Double>, counts: List<Long>): ImmutableHistogramData {
  return ImmutableHistogramData.create(
    AggregationTemporality.DELTA,
    listOf(createHistogramPointData(boundaries, counts))
  )
}

internal fun createHistogramPointData(boundaries: List<Double>, counts: List<Long>): ImmutableHistogramPointData {
  return ImmutableHistogramPointData.create(0, 0, Attributes.empty(), 100500.0,
                                            true, Double.NEGATIVE_INFINITY,
                                            true, Double.POSITIVE_INFINITY,
                                            boundaries, counts)
}

class OpenTelemetryHistogramPercentileTest {
  @Test
  fun testWhenPercentileIsZero() {
    val histogramData = createHistogramData(arrayListOf(1.0, 2.0, 3.0), arrayListOf(10, 20, 30, 5))
    histogramData.calculatePercentile(0) shouldBe 1.0
  }

  @Test
  fun testWhenPercentileIs100() {
    val histogramData = createHistogramData(arrayListOf(1.0, 2.0, 3.0), arrayListOf(10, 20, 30, 5))
    histogramData.calculatePercentile(100) shouldBe 3.0
  }

  @Test
  fun testWhenPercentileFallIntoSecondBucket() {
    val histogramData = createHistogramData(arrayListOf(1.0, 2.0, 3.0), arrayListOf(30, 40, 20, 10))
    histogramData.calculatePercentile(70) shouldBe 2.0
  }

  @Test
  fun testWithOneBoundary() {
    val histogramData = createHistogramData(arrayListOf(1.0), arrayListOf(100, 5))
    histogramData.calculatePercentile(95) shouldBe 1.0
  }

  @Test
  fun testWhenAllCountsAreZeros() {
    val histogramData = createHistogramData(arrayListOf(1.0, 2.0, 3.0), arrayListOf(0, 0, 0, 0))
    histogramData.calculatePercentile(50) shouldBe 1.0
  }

  // even buckets count

  @Test
  fun testTwoBucketsPercentile20() {
    val histogramData = createHistogramData(arrayListOf(1.0, 2.0), arrayListOf(50, 50, 0))
    histogramData.calculatePercentile(20) shouldBe 1.0
  }

  @Test
  fun testTwoBucketsPercentile50() {
    val histogramData = createHistogramData(arrayListOf(1.0, 2.0), arrayListOf(50, 50, 0))
    histogramData.calculatePercentile(50) shouldBe 1.0
  }

  @Test
  fun testTwoBucketsPercentile80() {
    val histogramData = createHistogramData(arrayListOf(1.0, 2.0), arrayListOf(50, 50, 0))
    histogramData.calculatePercentile(80) shouldBe 2.0
  }

  @Test
  fun testEvenBucketsCountPercentile30() {
    val histogramData = createHistogramData(arrayListOf(1.0, 2.0, 3.0, 4.0), arrayListOf(25, 25, 25, 25, 0))
    histogramData.calculatePercentile(30) shouldBe 2.0
  }

  @Test
  fun testEvenBucketsCountPercentile50() {
    val histogramData = createHistogramData(arrayListOf(1.0, 2.0, 3.0, 4.0), arrayListOf(25, 25, 25, 25, 0))
    histogramData.calculatePercentile(50) shouldBe 2.0
  }

  @Test
  fun testEvenBucketsCountPercentile80() {
    val histogramData = createHistogramData(arrayListOf(1.0, 2.0, 3.0, 4.0), arrayListOf(25, 25, 25, 25, 0))
    histogramData.calculatePercentile(80) shouldBe 4.0
  }
}