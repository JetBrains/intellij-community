// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint.instrumentation

import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.trace.breakpoint.ObjectStorage
import com.sun.jdi.Method
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value

/**
 * Source handler - prepares stream for tracing.
 * Makes stream sequential and adds initial tick peek to track time for subsequent operators.
 */
internal class StreamPreparer(
  private val objectStorage: ObjectStorage,
  private val time: ObjectReference,
) : SourceCallHandler {

  override fun afterCall(evaluationContextImpl: EvaluationContextImpl, value: Value?): Value? {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    return objectStorage.watch(evaluationContextImpl) {
      val streamObject = value as? ObjectReference ?: return@watch null
      val sequential = addSequentialOperator(streamObject)
      addTicker(sequential)
    }
  }

  private fun ValueContext.addTicker(streamObject: ObjectReference): ObjectReference {
    val ticker = instance(
      UNIVERSAL_COLLECTOR_CLASS_NAME,
      UNIVERSAL_COLLECTOR_CONSTRUCTOR_SIGNATURE,
      listOf(null, time, true.mirror)
    )
    return findPeekMethod(streamObject)
      .invoke(streamObject, listOf(ticker)) as ObjectReference
  }

  private fun findPeekMethod(streamObject: ObjectReference): Method {
    // methodsByName may throw ClassNotPreparedException, but by the time we execute this method,
    // it is guaranteed that the stream class is loaded
    return streamObject.referenceType().methodsByName("peek").single()
  }
}
