package com.intellij.debugger.streams.ui

import com.intellij.debugger.streams.trace.TraceElement

/**
 * @author Vitaliy.Bibaev
 */
interface ValueWithPosition {
  val traceElement: TraceElement

  val isVisible: Boolean
  val position: Int
  val isSelected: Boolean
}

