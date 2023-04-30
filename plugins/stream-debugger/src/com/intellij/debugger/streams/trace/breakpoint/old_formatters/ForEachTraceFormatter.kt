// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint.old_formatters

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.trace.breakpoint.ValueManager
import com.intellij.debugger.streams.wrapper.TerminatorStreamCall
import com.sun.jdi.Value

class ForEachTraceFormatter(
  valueManager: ValueManager,
  evaluationContext: EvaluationContextImpl
) : TraceFormatterBase(valueManager, evaluationContext), TerminationOperationTraceFormatter {
  override fun format(streamCall: TerminatorStreamCall,
                      collectedValues: StreamTraceValues,
                      beforeValues: Value?,
                      afterValues: Value?): Value {
    return formatBeforeAfter(beforeValues, streamCall.typeBefore.variableTypeName, afterValues, streamCall.resultType.variableTypeName)
  }

}