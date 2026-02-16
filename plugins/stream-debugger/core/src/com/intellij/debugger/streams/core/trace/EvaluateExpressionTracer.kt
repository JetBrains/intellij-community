// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.core.trace

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
  debugSession: XDebugSession,
  private val expressionBuilder: TraceExpressionBuilder,
  xValueInterpreter: XValueInterpreter,
  resultInterpreter: TraceResultInterpreter,
  private val debuggerCommandLauncher: DebuggerCommandLauncher,
) : AbstractStreamTracer(debugSession, xValueInterpreter, resultInterpreter) {

  override suspend fun trace(chain: StreamChain) : StreamTracer.Result = debuggerCommandLauncher.computeInDebuggerContext {
    val streamTraceExpression = withContext(Dispatchers.Default) { expressionBuilder.createTraceExpression(chain) }

    val stackFrame = debugSession.getCurrentStackFrame()
    val evaluator = debugSession.getDebugProcess().getEvaluator()

    if (stackFrame != null && evaluator != null) {
      val deferredResult = evaluateStreamExpression(evaluator, chain, streamTraceExpression, stackFrame)

      if (deferredResult.error == null) {
        val xValue = deferredResult.xValue ?: return@computeInDebuggerContext StreamTracer.Result.Unknown

        interpretStreamResult(xValue, chain, streamTraceExpression)
      }
      else {
        StreamTracer.Result.CompilationFailed(streamTraceExpression, deferredResult.error)
      }
    } else {
      StreamTracer.Result.Unknown
    }
  }

  data class EvaluationResult(val xValue: XValue?, @NlsSafe val error: String?)
  private suspend fun evaluateStreamExpression(
    evaluator: XDebuggerEvaluator,
    chain: StreamChain,
    streamTraceExpression: @NonNls String,
    stackFrame: XStackFrame,
  ): EvaluationResult = withContext(Dispatchers.Default) {
    val deferred = CompletableDeferred<EvaluationResult>()

    evaluator.evaluate(expressionBuilder.createXExpression(chain, streamTraceExpression), object : XEvaluationCallbackBase() {
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
