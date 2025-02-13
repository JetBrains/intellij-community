// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.sequence.trace.impl.interpret

import com.intellij.debugger.streams.core.trace.ArrayReference
import com.intellij.debugger.streams.core.trace.TraceElement
import com.intellij.debugger.streams.core.trace.Value
import com.intellij.debugger.streams.core.trace.impl.interpret.ex.UnexpectedValueException

object InterpreterUtil {

    fun extractMap(value: Value): MapRepresentation {
        if (value !is ArrayReference || value.length() != 2) {
            throw UnexpectedValueException("Map should be represented by array with two nested arrays: keys and values")
        }

        val keys = value.getValue(0)
        val values = value.getValue(1)

        if (keys !is ArrayReference || values !is ArrayReference || keys.length() != values.length()) {
            throw UnexpectedValueException("Keys and values should be arrays with equal counts of elements")
        }

        return MapRepresentation(keys, values)
    }

    fun createIndexByTime(elements: List<TraceElement>): Map<Int, TraceElement> {
        return elements.associateBy { elem -> elem.time }
    }

    data class MapRepresentation(val keys: ArrayReference, val values: ArrayReference)
}
