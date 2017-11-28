// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.ui.impl

import com.intellij.debugger.streams.trace.TraceElement
import com.intellij.debugger.streams.ui.ValueWithPosition

/**
 * @author Vitaliy.Bibaev
 */
data class ValueWithPositionImpl(override val traceElement: TraceElement) : ValueWithPosition {
  companion object {
    val INVALID_POSITION = Int.MIN_VALUE
    val DEFAULT_VISIBLE_VALUE = false
    val DEFAULT_HIGHLIGHTING_VALUE = false
  }

  private var myPosition: Int = INVALID_POSITION
  private var myIsVisible: Boolean = DEFAULT_VISIBLE_VALUE
  private var myIsHighlighted: Boolean = DEFAULT_HIGHLIGHTING_VALUE

  override fun equals(other: Any?): Boolean {
    return other != null && other is ValueWithPosition && traceElement == other.traceElement
  }

  override fun hashCode(): Int = traceElement.hashCode()

  override val isVisible: Boolean
    get() = myIsVisible

  override val position: Int
    get() = myPosition

  override val isHighlighted: Boolean
    get() = myIsHighlighted

  fun updateToInvalid(): Boolean =
    updateProperties(INVALID_POSITION, DEFAULT_VISIBLE_VALUE, DEFAULT_HIGHLIGHTING_VALUE)

  fun setInvalid() =
    setProperties(INVALID_POSITION, DEFAULT_VISIBLE_VALUE, DEFAULT_HIGHLIGHTING_VALUE)

  fun updateProperties(position: Int, isVisible: Boolean, isHighlighted: Boolean): Boolean {
    val changed = myPosition != position || myIsVisible != isVisible || myIsHighlighted != isHighlighted
    setProperties(position, isVisible, isHighlighted)
    return changed
  }

  fun setProperties(position: Int, isVisible: Boolean, isHighlighted: Boolean) {
    myPosition = position
    myIsHighlighted = isHighlighted
    myIsVisible = isVisible
  }
}