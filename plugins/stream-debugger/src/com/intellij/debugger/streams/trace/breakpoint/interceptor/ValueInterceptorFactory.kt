// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint.interceptor

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value

/**
 * @author Shumaf Lovpache
 */
interface ValueInterceptorFactory {
  fun getForIntermediate(evaluationContext: EvaluationContextImpl, collectorType: String): ValueInterceptor

  fun getForTermination(evaluationContext: EvaluationContextImpl): ValueInterceptor

  val collectedValues: StreamTraceValues
}

data class StreamTraceValues(
  val intermediateOperationValues: List<ObjectReference>,
  val streamResult: Value,
  val time: ObjectReference,
  val elapsedTime: Value
)