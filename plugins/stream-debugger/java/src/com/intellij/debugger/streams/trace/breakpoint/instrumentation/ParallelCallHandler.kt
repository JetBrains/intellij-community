// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint.instrumentation

import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.core.trace.impl.handler.type.GenericType
import com.intellij.debugger.streams.trace.breakpoint.ObjectStorage
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value

/**
 * Handles the `parallel()` intermediate operator.
 *
 * Parallel streams must be made sequential before wrapping with a collector,
 * otherwise element ordering would be non-deterministic and tracing would be unreliable.
 *
 * Transformation applied to the stream returned by `.parallel()`:
 * ```java
 * .parallel().sequential().peek(collector)
 * ```
 */
internal class ParallelCallHandler(
  objectStorage: ObjectStorage,
  typeBefore: GenericType?,
  typeAfter: GenericType?,
  time: ObjectReference,
) : PeekCallHandler(objectStorage, typeBefore, typeAfter, time) {

  override fun afterCall(evaluationContextImpl: EvaluationContextImpl, value: Value?): Value? {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    return objectStorage.watch(evaluationContextImpl) {
      val streamObject = value as? ObjectReference ?: return@watch value
      val sequentializedStream = addSequentialOperator(streamObject)
      super.afterCall(evaluationContextImpl, sequentializedStream)
    }
  }
}
