package com.intellij.debugger.streams.ui.impl

import com.intellij.debugger.streams.trace.TraceElement
import com.intellij.debugger.streams.ui.ValueWithPosition

/**
 * TODO: use data class
 * @author Vitaliy.Bibaev
 */
class ValueWithPositionImpl(override val traceElement: TraceElement) : ValueWithPosition {
  private var myPosition = -1
  private var myIsSelected = false
  private var myIsVisible = false

  override var isVisible: Boolean
    get() = myIsVisible
    set(value) {
      myIsVisible = value
    }

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