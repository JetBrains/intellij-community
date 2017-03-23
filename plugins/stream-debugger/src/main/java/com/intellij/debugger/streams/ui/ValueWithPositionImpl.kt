package com.intellij.debugger.streams.ui

import com.intellij.debugger.streams.trace.TraceElement

/**
 * @author Vitaliy.Bibaev
 */
class ValueWithPositionImpl(override val traceElement: TraceElement,
                            override val nextValues: List<ValueWithPosition>) : ValueWithPosition {
  private var myPosition = -1
  private var myIsSelected = false

  override val isVisible: Boolean
    get() = position == -1

  override var position: Int
    get() = myPosition
    set(value) {
      myPosition = value
    }

  override var isSelected: Boolean
    get() = myIsSelected
    set(value) {
      myIsSelected = value
    }
}