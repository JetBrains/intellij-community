// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.core.trace

import com.intellij.debugger.streams.core.StreamDebuggerBundle
import com.intellij.debugger.streams.core.wrapper.StreamChain
import com.intellij.openapi.util.NlsSafe
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.ui.tree.nodes.XEvaluationCallbackBase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls

/**
 * @author Vitaliy.Bibaev
 */
class EvaluateExpressionTracer(
  private val mySession: XDebugSession,
  private val myExpressionBuilder: TraceExpressionBuilder,
  private val myResultInterpreter: TraceResultInterpreter,
  private val myXValueInterpreter: XValueInterpreter,
) : StreamTracer {

  override suspend fun trace(chain: StreamChain) : StreamTracer.Result {
    val streamTraceExpression = withContext(Dispatchers.Default) { myExpressionBuilder.createTraceExpression(chain) }

    val stackFrame = mySession.getCurrentStackFrame()
    val evaluator = mySession.getDebugProcess().getEvaluator()

    if (stackFrame != null && evaluator != null) {
      val deferredResult = evaluateStreamExpression(evaluator, chain, streamTraceExpression, stackFrame)

      if (deferredResult.error == null) {
        val xValue = deferredResult.xValue ?: return StreamTracer.Result.Unknown

        val result = myXValueInterpreter.extract(mySession, xValue)
        when (result) {
          is XValueInterpreter.Result.Array -> {
            val interpretedResult: TracingResult
            try {
              interpretedResult = myResultInterpreter.interpret(chain, result.arrayReference, result.hasInnerExceptions)
            }
            catch (t: Throwable) {
              return StreamTracer.Result.EvaluationFailed(streamTraceExpression,
                                                          StreamDebuggerBundle.message("stream.debugger.evaluation.failed.cannot.interpret.result", t.message!!))
            }
            return StreamTracer.Result.Evaluated(interpretedResult, result.evaluationContext)
          }
          is XValueInterpreter.Result.Error -> {
            return StreamTracer.Result.EvaluationFailed(streamTraceExpression, result.message)
          }
          is XValueInterpreter.Result.Unknown -> {
            return StreamTracer.Result.EvaluationFailed(streamTraceExpression, StreamDebuggerBundle.message("stream.debugger.evaluation.failed", StreamDebuggerBundle.message("stream.debugger.evaluation.failed.unknown.type")))
          }
        }
      }
      else {
        return StreamTracer.Result.CompilationFailed(streamTraceExpression, deferredResult.error)
      }
    }

    return StreamTracer.Result.Unknown
  }

  data class EvaluationResult(val xValue: XValue?, @NlsSafe val error: String?)
  private suspend fun evaluateStreamExpression(
    evaluator: XDebuggerEvaluator,
    chain: StreamChain,
    streamTraceExpression: @NonNls String,
    stackFrame: XStackFrame,
  ): EvaluationResult = withContext(Dispatchers.Default) {
    val deferred = CompletableDeferred<EvaluationResult>()

    evaluator.evaluate(myExpressionBuilder.createXExpression(chain, streamTraceExpression), object : XEvaluationCallbackBase() {
      override fun evaluated(evaluationResult: XValue) {
        deferred.complete(EvaluationResult(evaluationResult, null))
      }

      override fun errorOccurred(errorMessage: String) {
        deferred.complete(EvaluationResult(null, errorMessage))
      }
    }, stackFrame.sourcePosition)

    val deferredResult = deferred.await()
    deferredResult
  }
}
