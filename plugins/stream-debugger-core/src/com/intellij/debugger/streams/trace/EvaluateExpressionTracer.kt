// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace;

import com.intellij.debugger.streams.StreamDebuggerBundle;
import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.ui.tree.nodes.XEvaluationCallbackBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vitaliy.Bibaev
 */
public class EvaluateExpressionTracer implements StreamTracer {
  private final XDebugSession mySession;
  private final TraceExpressionBuilder myExpressionBuilder;
  private final TraceResultInterpreter myResultInterpreter;
  private final XValueInterpreter myXValueInterpreter;

  public EvaluateExpressionTracer(@NotNull XDebugSession session,
                                  @NotNull TraceExpressionBuilder expressionBuilder,
                                  @NotNull TraceResultInterpreter interpreter,
                                  @NotNull XValueInterpreter xValueInterpreter) {
    mySession = session;
    myExpressionBuilder = expressionBuilder;
    myResultInterpreter = interpreter;
    myXValueInterpreter = xValueInterpreter;
  }

  @Override
  public void trace(@NotNull StreamChain chain, @NotNull TracingCallback callback) {
    final String streamTraceExpression = myExpressionBuilder.createTraceExpression(chain);

    final XStackFrame stackFrame = mySession.getCurrentStackFrame();
    final XDebuggerEvaluator evaluator = mySession.getDebugProcess().getEvaluator();
    if (stackFrame != null && evaluator != null) {
      evaluator.evaluate(myExpressionBuilder.createXExpression(chain, streamTraceExpression), new XEvaluationCallbackBase() {
        @Override
        public void evaluated(@NotNull XValue evaluationResult) {
          var result = myXValueInterpreter.tryExtractResult(mySession, evaluationResult);
          if (result != null) {
            final TracingResult interpretedResult;
            try {
              interpretedResult = myResultInterpreter.interpret(chain, result.getArrayReference(), result.getHasInnerExceptions());
            }
            catch (Throwable t) {
              callback.evaluationFailed(streamTraceExpression,
                                        StreamDebuggerBundle.message("evaluation.failed.cannot.interpret.result", t.getMessage()));
              throw t;
            }
            callback.evaluated(interpretedResult, result.getEvaluationContext());
          } else {
            @Nullable String errorDescription = myXValueInterpreter.tryExtractError(evaluationResult);
            if (errorDescription != null) {
              callback.evaluationFailed(streamTraceExpression, errorDescription);
            } else {
              callback.evaluationFailed(streamTraceExpression, StreamDebuggerBundle.message("evaluation.failed.unknown.result.type"));
            }
          }
        }

        @Override
        public void errorOccurred(@NotNull String errorMessage) {
          callback.compilationFailed(streamTraceExpression, errorMessage);
        }
      }, stackFrame.getSourcePosition());
    }
  }
}
