// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint.instrumentation

import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.core.wrapper.IntermediateStreamCall
import com.intellij.debugger.streams.core.wrapper.TerminatorStreamCall
import com.intellij.debugger.streams.trace.breakpoint.ObjectStorage
import com.sun.jdi.ObjectReference

interface BreakpointBasedHandlerFactory {
  /**
   * Called once on the debugger manager thread, immediately before stream tracing begins.
   *
   * The [evaluationContext] corresponds to the suspension point at which the stream chain
   * was intercepted (i.e., suspend context where user triggered stream tracing). It is valid only for
   * the duration of this call. Implementations __must not__ store [evaluationContext] or any
   * object derived from it as state since it is no longer valid once the thread resumes.
   */
  fun beforeStreamTracing(evaluationContext: EvaluationContextImpl)

  /**
   * Prepares stream for further transformation.
   * The stream can be parallel, so to be able to restore the order
   * of transformation, we need to make it sequential then add counter.
   */
  fun getForSource(): SourceCallHandler
  fun getForIntermediate(callOrder: Int, call: IntermediateStreamCall): IntermediateCallHandler
  fun getForTermination(call: TerminatorStreamCall): TerminalCallHandler
}

class CounterBasedBreakpointBasedHandlerFactory(
  private val objectStorage: ObjectStorage
) : BreakpointBasedHandlerFactory {
  // Global counter for elements flowing through the stream
  private lateinit var time: ObjectReference

  override fun beforeStreamTracing(evaluationContext: EvaluationContextImpl) {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    time = objectStorage.watch(evaluationContext) {
      instance("java.util.concurrent.atomic.AtomicInteger")
    }
  }

  override fun getForSource(): SourceCallHandler {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    return StreamPreparer(objectStorage, time)
  }

  override fun getForIntermediate(callOrder: Int, call: IntermediateStreamCall): IntermediateCallHandler {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    return when (call.name) {
      "parallel" -> ParallelCallHandler(objectStorage, call.getTypeBefore(), call.getTypeAfter(), time)
      else -> PeekCallHandler(objectStorage, call.getTypeBefore(), call.getTypeAfter(), time)
    }
  }

  override fun getForTermination(call: TerminatorStreamCall): TerminalCallHandler {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    return when (call.name) {
      "anyMatch", "allMatch", "noneMatch" -> MatchRuntimeHandler(call, objectStorage, time)
      "min", "max", "findAny", "findFirst" -> OptionalRuntimeHandler(call, objectStorage, time)
      else -> PeekTerminalCallHandler(objectStorage, call.getTypeBefore(), null, time)
    }
  }
}
