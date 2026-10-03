// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.performance

import org.junit.jupiter.api.fail
import kotlin.math.ln

/**
 * How the work of a scenario can grow with the size of its input. The growth is the exponent `e` of `work ~ size^e`.
 * Each bound has a slack of 0.2 for the terms of a lower order.
 */
enum class PyGrowth(private val maxExponent: Double) {
  CONSTANT(0.2),
  LINEAR(1.2),
  QUADRATIC(2.2),

  /** Has no bound. Only a [PyKnownDefect] uses it. */
  EXPONENTIAL(Double.POSITIVE_INFINITY);

  fun allows(exponent: Double): Boolean = exponent <= maxExponent
}

/**
 * An open performance issue that the expectation of a gate fails on. [growth] is the growth of the work with the defect.
 *
 * A gate with a known defect fails when the work is worse than the defect. It also fails when the expectation holds,
 * so the fix of the issue removes the known defect from the gate.
 */
class PyKnownDefect(val issue: String, val growth: PyGrowth = PyGrowth.EXPONENTIAL)

/**
 * Checks that the work of [scenario] grows with the input size as [expected].
 *
 * [work] gives the work for each of [sizes]. Use a counter of the calling thread, because it does not depend on the
 * machine. The growth is the exponent between the two largest sizes, `ln(w2 / w1) / ln(n2 / n1)`. A scenario that
 * expects a growth other than [PyGrowth.CONSTANT] must do work, so an empty scenario cannot pass. The check prints
 * the values with the prefix `PYGATE`.
 */
fun assertGrowth(scenario: String, sizes: List<Int>, expected: PyGrowth, knownDefect: PyKnownDefect? = null, work: (size: Int) -> Long) {
  require(sizes.size >= 2 && sizes.zipWithNext().all { (a, b) -> a < b }) { "Give two or more ascending sizes" }
  val values = sizes.map { it to work(it) }
  val (smaller, smallerWork) = values[values.size - 2]
  val (larger, largerWork) = values.last()
  if (expected != PyGrowth.CONSTANT) check(largerWork > 0) { "$scenario: the scenario does no work, the values: $values" }
  val exponent = when {
    largerWork == smallerWork -> 0.0
    smallerWork == 0L -> Double.POSITIVE_INFINITY
    else -> ln(largerWork.toDouble() / smallerWork) / ln(larger.toDouble() / smaller)
  }
  val measured = "the work for the sizes ${values.joinToString { (size, value) -> "$size: $value" }} grows with the exponent " +
                 "%.2f".format(exponent)
  println("PYGATE $scenario: $measured")
  checkExpectation(scenario, "the work grows as ${expected.name.lowercase()}: $measured", expected.allows(exponent), knownDefect) {
    growth.allows(exponent)
  }
}

/**
 * Checks that [holds] is true. With [knownDefect], checks that it is false: the expectation fails today. The check prints
 * the result with the prefix `PYGATE`.
 */
fun assertExpectation(scenario: String, expectation: String, holds: Boolean, knownDefect: PyKnownDefect? = null) {
  println("PYGATE $scenario: $expectation: ${if (holds) "holds" else "fails"}")
  checkExpectation(scenario, expectation, holds, knownDefect) { true }
}

private fun checkExpectation(
  scenario: String,
  expectation: String,
  holds: Boolean,
  knownDefect: PyKnownDefect?,
  withinDefect: PyKnownDefect.() -> Boolean,
) {
  when {
    knownDefect == null -> if (!holds) fail("$scenario: the expectation fails: $expectation")
    holds -> fail("$scenario: the expectation of the known defect ${knownDefect.issue} holds now: $expectation. " +
                  "Remove the known defect from the gate.")
    !knownDefect.withinDefect() -> fail("$scenario: the work is worse than the known defect ${knownDefect.issue} " +
                                        "(${knownDefect.growth.name.lowercase()}): $expectation")
  }
}
