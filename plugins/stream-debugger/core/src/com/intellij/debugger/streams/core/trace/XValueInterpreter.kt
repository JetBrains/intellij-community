// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.core.trace

import com.intellij.debugger.streams.core.StreamDebuggerBundle
import com.intellij.debugger.streams.core.wrapper.StreamChain
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.frame.XValue
import kotlinx.coroutines.CancellationException
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

interface GenericEvaluationContext {
  val project: Project
}

interface XValueInterpreter {
  sealed class Result {
    data class Array(val arrayReference: ArrayReference, val hasInnerExceptions: Boolean, val evaluationContext: GenericEvaluationContext) : Result()
    data class Error(@Nls val message: String) : Result()
    object Unknown : Result()
  }

  suspend fun extract(session: XDebugSession, result: XValue): Result
}

@ApiStatus.Internal
suspend fun interpretStreamResult(
  debugSession: XDebugSession,
  xValueInterpreter: XValueInterpreter,
  resultInterpreter: TraceResultInterpreter,
  xValue: XValue,
  chain: StreamChain,
  streamTraceExpression: @NonNls String = "",
): StreamTracer.Result {
  val result = xValueInterpreter.extract(debugSession, xValue)
  return when (result) {
    is XValueInterpreter.Result.Array -> {
      try {
        val interpretedResult = resultInterpreter.interpret(
          chain,
          result.arrayReference,
          result.hasInnerExceptions
        )
        StreamTracer.Result.Evaluated(interpretedResult, result.evaluationContext)
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (t: Throwable) {
        StreamTracer.Result.EvaluationFailed(
          streamTraceExpression,
          StreamDebuggerBundle.message("stream.debugger.evaluation.failed.cannot.interpret.result", t.message!!),
          t
        )
      }
    }
    is XValueInterpreter.Result.Error -> {
      StreamTracer.Result.EvaluationFailed(streamTraceExpression, result.message)
    }
    is XValueInterpreter.Result.Unknown -> {
      StreamTracer.Result.EvaluationFailed(
        streamTraceExpression,
        StreamDebuggerBundle.message("stream.debugger.evaluation.failed",
                                     StreamDebuggerBundle.message("stream.debugger.evaluation.failed.unknown.type"))
      )
    }
  }
}