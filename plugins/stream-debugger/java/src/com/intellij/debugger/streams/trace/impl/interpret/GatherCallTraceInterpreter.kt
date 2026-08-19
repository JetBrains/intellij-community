// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.impl.interpret

import com.intellij.debugger.streams.core.trace.ArrayReference
import com.intellij.debugger.streams.core.trace.CallTraceInterpreter
import com.intellij.debugger.streams.core.trace.IntegerValue
import com.intellij.debugger.streams.core.trace.TraceElement
import com.intellij.debugger.streams.core.trace.TraceInfo
import com.intellij.debugger.streams.core.trace.Value
import com.intellij.debugger.streams.core.trace.impl.interpret.SimplePeekCallTraceInterpreter
import com.intellij.debugger.streams.core.trace.impl.interpret.ValuesOrderInfo
import com.intellij.debugger.streams.core.trace.impl.interpret.ex.UnexpectedArrayLengthException
import com.intellij.debugger.streams.core.trace.impl.interpret.ex.UnexpectedValueException
import com.intellij.debugger.streams.core.trace.impl.interpret.ex.UnexpectedValueTypeException
import com.intellij.debugger.streams.core.wrapper.StreamCall
import com.intellij.debugger.streams.core.wrapper.TraceUtil

private fun extractIntArray(value: Value, description: String): List<Int> {
  if (value !is ArrayReference) {
    throw UnexpectedValueException("$description must be stored in an int array")
  }

  return value.getValues().filterNotNull().map { extractIntValue(it) }
}

private fun extractIntValue(value: Value): Int {
  if (value is IntegerValue) {
    return value.value()
  }

  throw UnexpectedValueTypeException("value should be Integer, but actual is " + value.typeName())
}

class GatherCallTraceInterpreter : CallTraceInterpreter {
  private val myPeekInterpreter: CallTraceInterpreter = SimplePeekCallTraceInterpreter()

  override fun resolve(call: StreamCall, value: Value): TraceInfo {
    if (value !is ArrayReference) {
      throw UnexpectedValueException("gather trace must be an array value")
    }
    // The payload contains the peek trace, source offsets, and source times.
    if (value.length() != 3) {
      throw UnexpectedArrayLengthException("gather trace must contain three elements")
    }

    val peekTrace = value.getValue(0) ?: throw UnexpectedValueException("peek trace must not be null")
    val sourceOffsetsTrace = value.getValue(1) ?: throw UnexpectedValueException("source offsets must not be null")
    val sourceTimesTrace = value.getValue(2) ?: throw UnexpectedValueException("source times must not be null")
    val order = myPeekInterpreter.resolve(call, peekTrace)

    val before = TraceUtil.sortedByTime(order.getValuesOrderBefore().values)
    val after = TraceUtil.sortedByTime(order.getValuesOrderAfter().values)
    val sourceOffsets = extractIntArray(sourceOffsetsTrace, "source offsets")
    val sourceTimes = extractIntArray(sourceTimesTrace, "source times")
    // There is one initial offset plus one end offset for every output element.
    if (sourceOffsets.size != after.size + 1) {
      throw UnexpectedArrayLengthException("length of sourceOffsets array should be one more than after trace size")
    }
    if (sourceOffsets.firstOrNull() != 0 || sourceOffsets.last() != sourceTimes.size ||
        sourceOffsets.zipWithNext().any { (start, end) -> start > end }) {
      throw UnexpectedValueException("source offsets must describe ordered ranges in the sourceTimes array")
    }

    val direct = LinkedHashMap<TraceElement, MutableList<TraceElement>>()
    for (beforeElement in before) {
      direct[beforeElement] = ArrayList()
    }

    val reverse = LinkedHashMap<TraceElement, MutableList<TraceElement>>()
    val beforeByTime = order.getValuesOrderBefore()
    for (i in after.indices) {
      val afterElement = after[i]
      val sourceElements = ArrayList<TraceElement>()
      for (sourceTime in sourceTimes.subList(sourceOffsets[i], sourceOffsets[i + 1])) {
        if (sourceTime >= 0) {
          val beforeElement = beforeByTime[sourceTime]
          if (beforeElement != null) {
            direct[beforeElement]!!.add(afterElement)
            sourceElements.add(beforeElement)
          }
        }
      }
      reverse[afterElement] = sourceElements
    }

    return ValuesOrderInfo(order.getCall(), order.getValuesOrderBefore(), order.getValuesOrderAfter(), direct, reverse)
  }
}
