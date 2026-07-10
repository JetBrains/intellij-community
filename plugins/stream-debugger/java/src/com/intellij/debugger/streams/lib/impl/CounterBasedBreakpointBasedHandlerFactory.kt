// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.lib.impl

import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.core.wrapper.IntermediateStreamCall
import com.intellij.debugger.streams.core.wrapper.TerminatorStreamCall
import com.intellij.debugger.streams.trace.breakpoint.ObjectStorage
import com.intellij.debugger.streams.trace.breakpoint.instrumentation.BreakpointBasedHandlerFactory
import com.intellij.debugger.streams.trace.breakpoint.instrumentation.IntermediateCallHandler
import com.intellij.debugger.streams.trace.breakpoint.instrumentation.PeekCallHandler
import com.intellij.debugger.streams.trace.breakpoint.instrumentation.SourceCallHandler
import com.intellij.debugger.streams.trace.breakpoint.instrumentation.StreamPreparer
import com.intellij.debugger.streams.trace.breakpoint.instrumentation.TerminalCallHandler
import com.sun.jdi.ObjectReference
import java.util.concurrent.atomic.AtomicInteger

/**
 * [BreakpointBasedHandlerFactory] driven by handler-producing functions.
 *
 * Intermediate dispatch: delegates to [getIntermediateHandler]; falls back to [PeekCallHandler]
 * when the function returns `null`.
 *
 * Terminal dispatch: delegates to [getTerminalHandler]. Callers must ensure
 * [canHandleChain][BreakpointBasedLibrarySupport.canHandleChain] returned `true` before tracing.
 */
internal class CounterBasedBreakpointBasedHandlerFactory(
  private val objectStorage: ObjectStorage,
  private val getSourceHandler: (time: ObjectReference) -> SourceCallHandler,
  private val getIntermediateHandler: (callOrder: Int, call: IntermediateStreamCall, time: ObjectReference) -> IntermediateCallHandler,
  private val getTerminalHandler: (call: TerminatorStreamCall, time: ObjectReference) -> TerminalCallHandler,
) : BreakpointBasedHandlerFactory {

  private lateinit var time: ObjectReference

  override fun beforeStreamTracing(evaluationContext: EvaluationContextImpl) {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    time = objectStorage.watch(evaluationContext) {
      instance(AtomicInteger::class.java)
    }
  }

  override fun getForSource(): SourceCallHandler {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    return getSourceHandler(time)
  }

  override fun getForIntermediate(callOrder: Int, call: IntermediateStreamCall): IntermediateCallHandler {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    return getIntermediateHandler(callOrder, call, time)
  }

  override fun getForTermination(call: TerminatorStreamCall): TerminalCallHandler {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    return getTerminalHandler(call, time)
  }
}
