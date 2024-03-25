package com.intellij.tools.ide.metrics.collector

import com.intellij.tools.ide.metrics.collector.metrics.mad
import com.intellij.tools.ide.metrics.collector.metrics.median
import com.intellij.tools.ide.metrics.collector.metrics.range
import com.intellij.tools.ide.metrics.collector.metrics.standardDeviation
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test


class OpenTelemetryHistogramStatisticsTest {
  // Median

  @Test
  fun testSingleBucket() {
    val histogramData = createHistogramData(listOf(10.0), listOf(50, 0))
    histogramData.median() shouldBe 10.0
  }

  @Test
  fun testEvenlyDistributedAcrossThreeBuckets() {
    val histogramData = createHistogramData(listOf(10.0, 20.0, 30.0), listOf(50, 50, 50, 0))
    histogramData.median() shouldBe 20.0
  }

  @Test
  fun testUnevenlyDistributedAcrossThreeBuckets() {
    val histogramData = createHistogramData(listOf(10.0, 20.0, 30.0), listOf(20, 80, 100, 0))
    histogramData.median() shouldBe 25.0
  }

  @Test
  fun testEvenDistributionTwoBuckets() {
    val histogramData = createHistogramData(listOf(10.0, 20.0), listOf(50, 50, 0))
    histogramData.median() shouldBe 15.0
  }

  @Test
  fun testUnevenDistributionTwoBuckets() {
    val histogramData = createHistogramData(listOf(10.0, 20.0), listOf(80, 20, 0))
    histogramData.median() shouldBe 10.0
  }

  @Test
  fun testEvenDistributionFourBuckets() {
    val histogramData = createHistogramData(listOf(10.0, 20.0, 30.0, 40.0), listOf(25, 25, 25, 25, 0))
    histogramData.median() shouldBe 25.0
  }

  @Test
  fun testUnevenDistributionFourBuckets() {
    val histogramData = createHistogramData(listOf(10.0, 20.0, 30.0, 40.0), listOf(18, 46, 25, 11, 0))
    histogramData.median() shouldBe 20.0
  }

  // Standard deviation

  @Test
  fun testSingleBucketStandardDeviation() {
    val histogramData = createHistogramData(listOf(10.0), listOf(50, 0))
    histogramData.standardDeviation() shouldBe 0.0
  }

  @Test
  fun testEvenDistributionStandardDeviation() {
    val histogramData = createHistogramData(listOf(10.0, 20.0, 30.0, 40.0), listOf(25, 25, 25, 25, 0))
    histogramData.standardDeviation() shouldBe 11
  }

  @Test
  fun testUnevenDistributionStandardDeviation() {
    val histogramData = createHistogramData(listOf(10.0, 20.0, 30.0), listOf(20, 80, 100, 0))
    histogramData.standardDeviation() shouldBe 6
  }

  // Median Absolute Deviation (MAD)

  @Test
  fun testSingleBucketMad() {
    val histogramData = createHistogramData(listOf(10.0), listOf(50, 0))
    histogramData.mad() shouldBe 0.0
  }

  @Test
  fun testEvenDistributionMad() {
    val histogramData = createHistogramData(listOf(10.0, 20.0, 30.0, 40.0), listOf(25, 25, 25, 25, 0))
    histogramData.mad() shouldBe 10.0
  }

  @Test
  fun testUnevenDistributionMad() {
    val histogramData = createHistogramData(listOf(10.0, 20.0, 30.0), listOf(20, 80, 100, 0))
    histogramData.mad() shouldBe 5.0
  }

  // Range

  @Test
  fun testSingleBucketRange() {
    val histogramData = createHistogramData(listOf(10.0), listOf(50, 0))
    histogramData.range() shouldBe 0.0
  }

  @Test
  fun testUniformDistributionRange() {
    val histogramData = createHistogramData(listOf(10.0, 20.0, 30.0, 40.0), listOf(25, 25, 25, 25, 0))
    histogramData.range() shouldBe 30.0
  }

  @Test
  fun testSkewedDistributionRange() {
    val histogramData = createHistogramData(listOf(10.0, 20.0, 30.0), listOf(100, 200, 300, 0))
    histogramData.range() shouldBe 20.0
  }
}