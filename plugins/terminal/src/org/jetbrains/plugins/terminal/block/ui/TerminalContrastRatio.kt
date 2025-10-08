// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.ui

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.util.TerminalSettingsFloatValueImpl

@ApiStatus.Experimental
interface TerminalContrastRatio {
  val value: Float

  fun toFormattedString(): String

  companion object {
    val MIN_VALUE: TerminalContrastRatio = ofFloat(1.0f)
    val MAX_VALUE: TerminalContrastRatio = ofFloat(21.0f)
    val DEFAULT_VALUE: TerminalContrastRatio = ofFloat(4.5f)

    fun ofFloat(value: Float): TerminalContrastRatio {
      val clampedValue = value.coerceIn(1.0f, 21.0f)
      return TerminalContrastRatioImpl(TerminalSettingsFloatValueImpl.ofFloat(clampedValue, 2))
    }
  }
}

internal data class TerminalContrastRatioImpl(private val impl: TerminalSettingsFloatValueImpl) : TerminalContrastRatio {
  override val value: Float
    get() = impl.toFloat()

  override fun toFormattedString(): String {
    return impl.toFormattedString()
  }
}