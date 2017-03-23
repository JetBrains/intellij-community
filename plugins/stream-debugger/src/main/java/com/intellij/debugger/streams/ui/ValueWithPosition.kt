package com.intellij.debugger.streams.ui

import com.intellij.debugger.streams.trace.TraceElement

/**
 * @author Vitaliy.Bibaev
 */
interface ValueWithPosition {
  val traceElement: TraceElement
  val nextValues: List<ValueWithPosition>

  val position: Int
  val isVisible: Boolean
  val isSelected: Boolean
}