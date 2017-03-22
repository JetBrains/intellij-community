package com.intellij.debugger.streams.ui

import com.intellij.debugger.streams.trace.TraceElement

/**
 * @author Vitaliy.Bibaev
 */
interface ValueWithPosition {
  fun getTraceElement(): TraceElement
  fun getPosition(): Int
}
