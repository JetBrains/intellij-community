package com.intellij.debugger.streams.ui.impl

import com.intellij.debugger.streams.trace.TraceElement
import com.intellij.debugger.streams.ui.ValueWithPosition

/**
 * @author Vitaliy.Bibaev
 */
data class ValueWithPositionImpl(override val traceElement: TraceElement,
                                 override var isVisible: Boolean = false,
                                 override var position: Int = -1,
                                 override var isSelected: Boolean = false) : ValueWithPosition {
}