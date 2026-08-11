// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint.instrumentation

import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.core.wrapper.TerminatorStreamCall
import com.intellij.debugger.streams.trace.breakpoint.ObjectStorage
import com.sun.jdi.ArrayReference
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value

internal open class ToCollectionTerminalCallHandler(
  call: TerminatorStreamCall,
  objectStorage: ObjectStorage,
  time: ObjectReference,
) : PeekTerminalCallHandler(objectStorage, call.getTypeBefore(), null, time) {

  override fun result(evaluationContextImpl: EvaluationContextImpl): Value {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    return objectStorage.watch(evaluationContextImpl) {
      val (beforeAfter, wrappedResult) = rawResult(evaluationContextImpl)
      val timing = formatTime()
      array(
        array(beforeAfter, timing),
        wrappedResult
      )
    }
  }

  private fun ValueContext.formatTime(): ArrayReference {
    val getMethod = time.referenceType().method("get", "()I")
    val timeValue = getMethod.invoke(time)

    return array("int", 1).apply {
      setValue(0, timeValue)
    }
  }
}
