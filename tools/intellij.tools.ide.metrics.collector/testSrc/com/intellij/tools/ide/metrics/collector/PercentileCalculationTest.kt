package com.intellij.tools.ide.metrics.collector

import com.intellij.tools.ide.metrics.collector.metrics.percentile
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PercentileCalculationTest {
  @Test
  fun `percentile of empty collection throws IllegalArgumentException`() {
    shouldThrow<IllegalArgumentException> {
      emptyList<Int>().percentile(50)
    }
  }

  @Test
  fun `percentile of single-element collection is the element`() {
    assertSoftly {
      listOf(15).percentile(50) shouldBe 15
      listOf(15).percentile(0) shouldBe 15
      listOf(15).percentile(100) shouldBe 15
    }
  }

  @Test
  fun `percentile out of bounds throws IllegalArgumentException`() {
    shouldThrow<IllegalArgumentException> {
      listOf(1, 2, 3).percentile(-1)
    }

    shouldThrow<IllegalArgumentException> {
      listOf(1, 2, 3).percentile(101)
    }
  }

  @Test
  fun `percentile calculation for multi-element collection`() {
    val data = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)

    assertSoftly {
      data.percentile(0) shouldBe 1
      data.percentile(10) shouldBe 2
      data.percentile(25) shouldBe 3
      data.percentile(50) shouldBe 6
      data.percentile(75) shouldBe 8
      data.percentile(90) shouldBe 9
      data.percentile(100) shouldBe 10
    }
  }

  @Test
  fun `percentile calculation for collection with repeated elements`() {
    val data = listOf(1, 1, 1, 1, 1, 6, 7, 8, 9, 10)

    assertSoftly {
      data.percentile(0) shouldBe 1
      data.percentile(50) shouldBe 6
      data.percentile(60) shouldBe 6
      data.percentile(70) shouldBe 7
      data.percentile(100) shouldBe 10
    }
  }

  @Test
  fun `percentile of 2-element collection`() {
    val data = listOf(5, 10)

    assertSoftly {
      data.percentile(0) shouldBe 5
      data.percentile(25) shouldBe 5
      data.percentile(50) shouldBe 10
      data.percentile(75) shouldBe 10
      data.percentile(100) shouldBe 10
    }
  }

  @Test
  fun `percentile of 4-element collection`() {
    val data = listOf(1, 3, 5, 7)

    assertSoftly {
      data.percentile(0) shouldBe 1
      data.percentile(25) shouldBe 3
      data.percentile(50) shouldBe 5
      data.percentile(75) shouldBe 5
      data.percentile(100) shouldBe 7
    }
  }

  @Test
  fun `percentile with negative numbers is correct`() {
    val data = listOf(-5, -3, -1, 2, 4)

    assertSoftly {
      data.percentile(25) shouldBe -3
      data.percentile(50) shouldBe -1
      data.percentile(75) shouldBe 2
    }
  }

  @Test
  fun `percentile outside valid range throws exception`() {
    val data = listOf(1, 2, 3)
    shouldThrow<IllegalArgumentException> {
      data.percentile(-1)
    }
    shouldThrow<IllegalArgumentException> {
      data.percentile(101)
    }
  }

  @Test
  fun `test single element`() {
    val data = listOf(20)
    assertEquals(20, data.percentile(50))
    assertEquals(20, data.percentile(10))
    assertEquals(20, data.percentile(90))
  }

  @Test
  fun `test interpolated position 2`() {
    val data = listOf(20, 21, 25, 21, 27, 21, 28, 29, 21, 29, 20, 30, 27, 30, 21, 33, 23, 36, 19, 39, 27, 40)
    assertEquals(36, data.percentile(90))
  }

  @Test
  fun `test invalid percentile`() {
    val data = listOf(10, 20, 30, 40, 50)
    assertThrows<IllegalArgumentException> {
      data.percentile(-1)
    }
    assertThrows<IllegalArgumentException> {
      data.percentile(101)
    }
  }
}