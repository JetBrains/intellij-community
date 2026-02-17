// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint.instrumentation

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.core.wrapper.IntermediateStreamCall
import com.intellij.debugger.streams.core.wrapper.TerminatorStreamCall
import com.sun.jdi.Method
import com.sun.jdi.Value

// TODO: include time in the implementation
class BreakpointBasedHandlerFactory {
  /**
   * Prepares stream for further transformation.
   * The stream can be parallel, so to be able to restore the order
   * of transformation, we need to make it sequential then add counter.
   */
  fun getForSource(): SourceCallHandler = NopHandler
  fun getForIntermediate(number: Int, call: IntermediateStreamCall): IntermediateCallHandler = NopHandler
  fun getForTermination(call: TerminatorStreamCall): TerminalCallHandler = NopHandler
}