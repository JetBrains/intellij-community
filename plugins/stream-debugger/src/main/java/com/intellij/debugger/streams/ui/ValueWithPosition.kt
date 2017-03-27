package com.intellij.debugger.streams.ui

import com.intellij.debugger.streams.trace.TraceElement

/**
 * @author Vitaliy.Bibaev
 */
interface ValueWithPosition {
  val traceElement: TraceElement

  var isVisible: Boolean
  var position: Int
  var isSelected: Boolean
}

