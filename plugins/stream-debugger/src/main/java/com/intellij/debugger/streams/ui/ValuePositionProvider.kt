package com.intellij.debugger.streams.ui

import com.intellij.debugger.streams.trace.TraceElement

/**
 * @author Vitaliy.Bibaev
 */
interface ValuePositionProvider {
  fun getValues(): List<ValueWithPosition>
  fun getPositionByValue(value: TraceElement): Int
}