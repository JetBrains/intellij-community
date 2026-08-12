package com.intellij.tools.ide.metrics.statistics

import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt

open class CompareInterval(val min: Double, val max: Double) {
  val middle get() = (max - min) / 2

  companion object {
    private fun calcPercentile(sortedValues: List<Double>, percentile: Int): Double {
      val realIndex = percentile / 100.0 * (sortedValues.size - 1)
      val index = realIndex.toInt()
      val frac = realIndex - index
      return if (index + 1 < sortedValues.size) sortedValues[index] * (1 - frac) + sortedValues[index + 1] * frac else sortedValues[index]
    }

    fun calc(a: List<Double>, b: List<Double>, calc: (Double, Double) -> Double): CompareInterval? {
      if (a.isEmpty() || b.isEmpty())
        return null
      val sa = a.sorted()
      val sb = b.sorted()
      var minR = Double.MAX_VALUE
      var maxR = -Double.MAX_VALUE
      for (i in 25..75) {
        val r = calc(calcPercentile(sa, i), calcPercentile(sb, i))
        minR = min(minR, r)
        maxR = max(maxR, r)
      }
      return CompareInterval(minR, maxR)
    }
  }
}

class RatioInterval(min: Double, max: Double) : CompareInterval(min, max) {
  companion object {
    fun calc(a: List<Double>, b: List<Double>) = calc(a, b) { pa, pb -> pb / pa }?.let { RatioInterval(it.min, it.max) }
  }
}

class ShiftInterval(min: Double, max: Double) : CompareInterval(min, max) {
  companion object {
    fun calc(a: List<Double>, b: List<Double>) = calc(a, b) { pa, pb -> pb - pa }?.let { ShiftInterval(it.min, it.max) }
  }
}

fun RatioInterval?.toNiceString(format: (Double) -> String): String {
  if (this == null)
    return "?"
  val minP = round((this.min - 1) * 100)
  val maxP = round((this.max - 1) * 100)
  if (maxP - minP <= 1)
    return format(maxP)
  val roundedMinP = Perfolizer.Rounder.roundDown(minP).toDouble()
  val roundedMaxP = Perfolizer.Rounder.roundUp(maxP).toDouble()
  return "${format(roundedMinP)}..${format(roundedMaxP)}"
}

fun ShiftInterval?.toNiceString(format: (Double) -> String): String {
  if (this == null)
    return "?"
  val minP = round(this.min)
  val maxP = round(this.max)
  if (maxP - minP <= 1)
    return format(maxP)
  return "${format(minP)}..${format(maxP)}"
}

fun RatioInterval?.toNiceRatioString(): String {
  if (this != null && (this.min.isNaN() || this.max.isNaN()))
    return "NaN"
  return this.toNiceString { if (it > 0) "+${it.roundToInt()}" else "${it.roundToInt()}" } + "%"
}

fun ShiftInterval?.toNiceShiftString(): String {
  if (this != null && (this.min.isNaN() || this.max.isNaN()))
    return "NaN"
  return this.toNiceString { if (it > 0) "+${it.roundToInt()}" else "${it.roundToInt()}" }
}
