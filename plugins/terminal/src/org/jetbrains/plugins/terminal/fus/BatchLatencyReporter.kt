// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.fus

import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration

@ApiStatus.Internal
class BatchLatencyReporter<T>(private val batchSize: Int, private val doReport: (MutableList<T>) -> Unit) {
  private val samples: MutableList<T?> = MutableList(batchSize) { null }
  private var curIndex = 0

  fun update(value: T) {
    samples[curIndex] = value
    curIndex++

    if (curIndex == batchSize) {
      @Suppress("UNCHECKED_CAST") // We guarantee that all values are not null
      doReport(samples as MutableList<T>)
      curIndex = 0
    }
  }
}

@ApiStatus.Internal
fun <T : Comparable<T>> List<T>.secondLargest(): T {
  check(isNotEmpty()) { "It is expected that array is not empty" }

  var max = get(0)
  var secondLargest = get(0)
  for (value in this) {
    if (value > max) {
      secondLargest = max
      max = value
    }
    else if (value > secondLargest) {
      secondLargest = value
    }
  }

  return secondLargest
}

@ApiStatus.Internal
fun <T : Comparable<T>> List<T>.thirdLargest(): T {
  return thirdLargestOf { it }
}

@ApiStatus.Internal
inline fun <T, C : Comparable<C>> List<T>.thirdLargestOf(selector: (T) -> C): C {
  check(isNotEmpty()) { "It is expected that array is not empty" }

  var max = selector(get(0))
  var secondLargest = max
  var thirdLargest = max
  for (value in this) {
    val comparableValue = selector(value)
    if (comparableValue > max) {
      thirdLargest = secondLargest
      secondLargest = max
      max = comparableValue
    }
    else if (comparableValue > secondLargest) {
      thirdLargest = secondLargest
      secondLargest = comparableValue
    }
    else if (comparableValue > thirdLargest) {
      thirdLargest = comparableValue
    }
  }

  return thirdLargest
}

/**
 * NOTE: it mutates the original list by sorting it in-place.
 */
@ApiStatus.Internal
fun <T : Comparable<T>> MutableList<T>.percentile(n: Int): T {
  return percentileOf(n) { it }
}

/**
 * NOTE: it mutates the original list by sorting it in-place.
 */
@ApiStatus.Internal
inline fun <T, C : Comparable<C>> MutableList<T>.percentileOf(n: Int, crossinline selector: (T) -> C): C {
  check(n in 0..100) { "Percentile must be in [0..100]" }

  sortBy(selector)
  val index = (size * n / 100).coerceAtMost(size - 1)
  return selector(get(index))
}

@ApiStatus.Internal
fun List<Duration>.totalDuration(): Duration {
  return totalDurationOf { it }
}

@ApiStatus.Internal
inline fun <T> List<T>.totalDurationOf(selector: (T) -> Duration): Duration {
  return fold(Duration.ZERO) { acc, value -> acc + selector(value) }
}

@ApiStatus.Internal
data class DurationAndTextLength(val duration: Duration, val textLength: Int)