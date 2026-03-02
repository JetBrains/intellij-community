// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint.instrumentation

import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.core.trace.impl.handler.type.GenericType
import com.intellij.debugger.streams.trace.breakpoint.ObjectStorage
import com.intellij.psi.CommonClassNames
import com.sun.jdi.ArrayReference
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value
import com.sun.jdi.VoidValue

/**
 * Terminal handler that captures the final stream result along with trace.
 */
internal open class PeekTerminalCallHandler(
  objectStorage: ObjectStorage,
  typeBefore: GenericType?,
  resultType: GenericType?,
  time: ObjectReference
) : PeekCallHandler(objectStorage, typeBefore, resultType, time), TerminalCallHandler {

  protected var streamResult: Value? = null

  override fun afterCall(
    evaluationContextImpl: EvaluationContextImpl,
    value: Value?
  ): Value? {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    streamResult = value  // Capture terminal result
    return value
  }

  override fun result(evaluationContextImpl: EvaluationContextImpl): Value {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    return objectStorage.watch(evaluationContextImpl) {
      val (beforeAfter, wrappedResult) = rawResult(evaluationContextImpl)
      // Format: [intermediateTrace, terminalResult]
      array(beforeAfter, wrappedResult)
    }
  }

  /**
   * Returns the two sub-arrays that compose the terminal handler result:
   * - first: intermediate trace (infos array)
   * - second: the wrapped terminal result
   *
   * Subclasses can call this from within an `objectStorage.watch` block to build
   * customized result structures (e.g. [OptionalRuntimeHandler]).
   */
  protected fun ValueContext.rawResult(evaluationContextImpl: EvaluationContextImpl): Pair<ArrayReference, ArrayReference> {
    val intermediateTrace = super.result(evaluationContextImpl) as ArrayReference
    val wrappedResult = when (val r = streamResult) {
      is VoidValue -> array(CommonClassNames.JAVA_LANG_OBJECT, 1)
      else -> array(r)
    }
    return Pair(intermediateTrace, wrappedResult)
  }
}
