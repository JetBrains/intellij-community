// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint.instrumentation

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.core.wrapper.IntermediateStreamCall
import com.intellij.debugger.streams.core.wrapper.TerminatorStreamCall

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
