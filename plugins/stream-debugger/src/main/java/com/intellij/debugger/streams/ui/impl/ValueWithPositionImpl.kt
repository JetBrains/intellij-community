package com.intellij.debugger.streams.ui.impl

import com.intellij.debugger.streams.trace.TraceElement
import com.intellij.debugger.streams.ui.ValueWithPosition

/**
 * @author Vitaliy.Bibaev
 */
data class ValueWithPositionImpl(override val traceElement: TraceElement) : ValueWithPosition {
  companion object {
    val INVALID_POSITION = Int.MIN_VALUE
    val DEFAULT_SELECTED_VALUE = false
    val DEFAULT_VISIBLE_VALUE = false
  }

  private var myPosition: Int = INVALID_POSITION
  private var myIsSelected: Boolean = DEFAULT_SELECTED_VALUE
  private var myIsVisible: Boolean = DEFAULT_VISIBLE_VALUE

  override fun equals(other: Any?): Boolean {
    return other != null && other is ValueWithPosition && traceElement == other.traceElement
  }

  override fun hashCode(): Int = traceElement.hashCode()

  override val isVisible: Boolean
    get() = myIsVisible

  override val position: Int
    get() = myPosition

  override val isSelected: Boolean
    get() = myIsSelected

  fun updateToInvalid(): Boolean =
    updateProperties(INVALID_POSITION, DEFAULT_VISIBLE_VALUE, DEFAULT_SELECTED_VALUE)

  fun setInvalid(): Unit =
    setProperties(INVALID_POSITION, DEFAULT_VISIBLE_VALUE, DEFAULT_SELECTED_VALUE)

  fun updateProperties(position: Int, isVisible: Boolean, isSelected: Boolean): Boolean {
    val changed = myPosition != position || myIsVisible != isVisible || myIsSelected != isSelected
    setProperties(position, isVisible, isSelected)
    return changed
  }

  fun setProperties(position: Int, isVisible: Boolean, isSelected: Boolean): Unit {
    myPosition = position
    myIsSelected = isSelected
    myIsVisible = isVisible
  }
}