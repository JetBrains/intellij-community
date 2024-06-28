package com.intellij.tools.ide.metrics.collector

import com.intellij.tools.ide.metrics.collector.metrics.percentile
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PercentileCalculationTest {
  @Test
  fun `percentile of empty collection throws IllegalArgumentException`() {
    shouldThrow<IllegalArgumentException> {
      emptyList<Long>().percentile(50)
    }
  }

  @Test
  fun `percentile of single-element collection is the element`() {
    assertSoftly {
      listOf(15L).percentile(50) shouldBe 15L
      listOf(15L).percentile(0) shouldBe 15L
      listOf(15L).percentile(100) shouldBe 15L
    }
  }

  @Test
  fun `percentile out of bounds throws IllegalArgumentException`() {
    shouldThrow<IllegalArgumentException> {
      listOf(1L, 2L, 3L).percentile(-1)
    }

    shouldThrow<IllegalArgumentException> {
      listOf(1L, 2L, 3L).percentile(101)
    }
  }

  @Test
  fun `percentile calculation for multi-element collection`() {
    val data = listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L)

    assertSoftly {
      data.percentile(0) shouldBe 1L
      data.percentile(10) shouldBe 2L
      data.percentile(25) shouldBe 3L
      data.percentile(50) shouldBe 6L
      data.percentile(75) shouldBe 8L
      data.percentile(90) shouldBe 9L
      data.percentile(100) shouldBe 10L
    }
  }

  @Test
  fun `percentile calculation for collection with repeated elements`() {
    val data = listOf(1L, 1L, 1L, 1L, 1L, 6L, 7L, 8L, 9L, 10L)

    assertSoftly {
      data.percentile(0) shouldBe 1L
      data.percentile(50) shouldBe 6L
      data.percentile(60) shouldBe 6L
      data.percentile(70) shouldBe 7L
      data.percentile(100) shouldBe 10L
    }
  }

  @Test
  fun `percentile of 2-element collection`() {
    val data = listOf(5L, 10L)

    assertSoftly {
      data.percentile(0) shouldBe 5L
      data.percentile(25) shouldBe 5L
      data.percentile(50) shouldBe 10L
      data.percentile(75) shouldBe 10L
      data.percentile(100) shouldBe 10L
    }
  }

  @Test
  fun `percentile of 4-element collection`() {
    val data = listOf(1L, 3L, 5L, 7L)

    assertSoftly {
      data.percentile(0) shouldBe 1L
      data.percentile(25) shouldBe 3L
      data.percentile(50) shouldBe 5L
      data.percentile(75) shouldBe 5L
      data.percentile(100) shouldBe 7L
    }
  }

  @Test
  fun `percentile with negative numbers is correct`() {
    val data = listOf(-5L, -3L, -1L, 2L, 4L)

    assertSoftly {
      data.percentile(25) shouldBe -3L
      data.percentile(50) shouldBe -1L
      data.percentile(75) shouldBe 2L
    }
  }

  @Test
  fun `percentile outside valid range throws exception`() {
    val data = listOf(1L, 2L, 3L)
    shouldThrow<IllegalArgumentException> {
      data.percentile(-1)
    }
    shouldThrow<IllegalArgumentException> {
      data.percentile(101)
    }
  }
}