// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace

import com.intellij.debugger.streams.StreamDebuggerBundle
import com.intellij.debugger.streams.wrapper.StreamChain
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.ui.tree.nodes.XEvaluationCallbackBase

/**
 * @author Vitaliy.Bibaev
 */
class EvaluateExpressionTracer(
  private val mySession: XDebugSession,
  private val myExpressionBuilder: TraceExpressionBuilder,
  private val myResultInterpreter: TraceResultInterpreter,
  private val myXValueInterpreter: XValueInterpreter,
) : StreamTracer {
  override fun trace(chain: StreamChain, callback: TracingCallback) {
    val streamTraceExpression = myExpressionBuilder.createTraceExpression(chain)

    val stackFrame = mySession.getCurrentStackFrame()
    val evaluator = mySession.getDebugProcess().getEvaluator()
    if (stackFrame != null && evaluator != null) {
      evaluator.evaluate(myExpressionBuilder.createXExpression(chain, streamTraceExpression), object : XEvaluationCallbackBase() {
        override fun evaluated(evaluationResult: XValue) {
          val result = myXValueInterpreter.extract(mySession, evaluationResult)
          when (result) {
            is XValueInterpreter.Result.Array -> {
              val interpretedResult: TracingResult
              try {
                interpretedResult = myResultInterpreter.interpret(chain, result.arrayReference, result.hasInnerExceptions)
              }
              catch (t: Throwable) {
                callback.evaluationFailed(streamTraceExpression,
                                          StreamDebuggerBundle.message("evaluation.failed.cannot.interpret.result", t.message!!))
                throw t
              }
              callback.evaluated(interpretedResult, result.evaluationContext)
            }
            is XValueInterpreter.Result.Error -> {
              callback.evaluationFailed(streamTraceExpression, result.message)
            }
            is XValueInterpreter.Result.Unknown -> {
              callback.evaluationFailed(streamTraceExpression, StreamDebuggerBundle.message("evaluation.failed.unknown.result.type"))
            }
          }
        }

        override fun errorOccurred(errorMessage: String) {
          callback.compilationFailed(streamTraceExpression, errorMessage)
        }
      }, stackFrame.getSourcePosition())
    }
  }
}
