// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.util

import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * A container for floating-point values with equality support and sensible precision.
 */
internal data class TerminalSettingsFloatValueImpl(
  private val rawIntValue: Int,
  private val digits: Int,
) {
  companion object {
    fun ofFloat(value: Float, digits: Int): TerminalSettingsFloatValueImpl =
      TerminalSettingsFloatValueImpl(rawIntValue = (value * multiplier(digits)).roundToInt(), digits = digits)

    fun parse(value: String, defaultValue: Float, digits: Int): TerminalSettingsFloatValueImpl =
      try {
        ofFloat(value.toFloat(), digits)
      }
      catch (_: Exception) {
        ofFloat(defaultValue, digits)
      }

    private fun multiplier(digits: Int): Float = 10f.pow(digits)
  }

  private val multiplier: Float = multiplier(digits)

  private val actualDigits: Int
    get() {
      var actualDigits = digits
      var value = rawIntValue
      while (actualDigits > 1 && value % 10 == 0) {
        --actualDigits
        value /= 10
      }
      return actualDigits
    }

  fun coerceIn(range: ClosedFloatingPointRange<Float>): TerminalSettingsFloatValueImpl =
    ofFloat(toFloat().coerceIn(range), digits)

  fun toFloat(): Float = rawIntValue.toFloat() / multiplier

  fun toFormattedString(): String = String.format(Locale.ROOT, "%.${actualDigits}f", toFloat())
}