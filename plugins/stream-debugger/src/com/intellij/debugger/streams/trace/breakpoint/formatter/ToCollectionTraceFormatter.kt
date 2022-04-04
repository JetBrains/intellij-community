// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint.formatter

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.trace.breakpoint.ValueManager
import com.intellij.debugger.streams.trace.breakpoint.interceptor.StreamTraceValues
import com.intellij.debugger.streams.wrapper.TerminatorStreamCall
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value

class ToCollectionTraceFormatter(
  private val valueManager: ValueManager,
  private val evaluationContext: EvaluationContextImpl
  ) : TraceFormatterBase(valueManager, evaluationContext), TerminationOperationTraceFormatter {

  /**
   * Converts the result of the intermediate operation to the following format:
   * ```
   * var beforeArray = // same as in PeekTraceFormatter
   * var afterArray = // same as in PeekTraceFormatter
   * new java.lang.Object[] { beforeArray, afterArray, time };
   * ```
   */
  override fun format(streamCall: TerminatorStreamCall,
                      collectedValues: StreamTraceValues,
                      beforeValues: Value?,
                      afterValues: Value?): Value = valueManager.watch(evaluationContext) {
    val before = super.formatMap(beforeValues, streamCall.typeBefore.variableTypeName)
    val after = super.formatMap(afterValues, streamCall.resultType.variableTypeName)

    val formattedTime = formatTime(collectedValues.time)
    array(
      array(before, after),
      formattedTime
    )
  }

  private fun formatTime(time: ObjectReference): Value = valueManager.watch(evaluationContext) {
    val getTime = time.method("get", "()I")
    val timeValue = getTime.invoke(time, emptyList())
    val timeArr = array("int", 1)
    timeArr.setValue(0, timeValue)
    timeArr
  }
}