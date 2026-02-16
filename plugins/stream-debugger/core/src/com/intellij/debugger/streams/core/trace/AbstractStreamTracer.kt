package com.intellij.debugger.streams.core.trace

import com.intellij.debugger.streams.core.StreamDebuggerBundle
import com.intellij.debugger.streams.core.wrapper.StreamChain
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.frame.XValue
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

@ApiStatus.Internal
abstract class AbstractStreamTracer(
  val debugSession: XDebugSession,
  val xValueInterpreter: XValueInterpreter,
  val resultInterpreter: TraceResultInterpreter,
) : StreamTracer {
  protected suspend fun interpretStreamResult(
    xValue: XValue,
    chain: StreamChain,
    streamTraceExpression: @NonNls String,
  ): StreamTracer.Result {
    val result = xValueInterpreter.extract(debugSession, xValue)
    when (result) {
      is XValueInterpreter.Result.Array -> {
        val interpretedResult: TracingResult
        try {
          interpretedResult = resultInterpreter.interpret(chain, result.arrayReference, result.hasInnerExceptions)
        }
        catch (t: Throwable) {
          return StreamTracer.Result.EvaluationFailed(streamTraceExpression,
                                                      StreamDebuggerBundle.message("stream.debugger.evaluation.failed.cannot.interpret.result",
                                                                                   t.message!!))
        }
        return StreamTracer.Result.Evaluated(interpretedResult, result.evaluationContext)
      }
      is XValueInterpreter.Result.Error -> {
        return StreamTracer.Result.EvaluationFailed(streamTraceExpression, result.message)
      }
      is XValueInterpreter.Result.Unknown -> {
        return StreamTracer.Result.EvaluationFailed(streamTraceExpression,
                                                    StreamDebuggerBundle.message("stream.debugger.evaluation.failed",
                                                                                 StreamDebuggerBundle.message("stream.debugger.evaluation.failed.unknown.type")))
      }
    }
  }
}
