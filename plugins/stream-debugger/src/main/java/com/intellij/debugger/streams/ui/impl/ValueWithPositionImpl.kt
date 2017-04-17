/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  fun setInvalid(): Unit =
    setProperties(INVALID_POSITION, DEFAULT_VISIBLE_VALUE, DEFAULT_HIGHLIGHTING_VALUE)

  fun updateProperties(position: Int, isVisible: Boolean, isHighlighted: Boolean): Boolean {
    val changed = myPosition != position || myIsVisible != isVisible || myIsHighlighted != isHighlighted
    setProperties(position, isVisible, isHighlighted)
    return changed
  }

  fun setProperties(position: Int, isVisible: Boolean, isHighlighted: Boolean): Unit {
    myPosition = position
    myIsHighlighted = isHighlighted
    myIsVisible = isVisible
  }
}