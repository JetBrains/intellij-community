package com.intellij.debugger.streams.trace;

import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.intellij.xdebugger.impl.ui.tree.nodes.XEvaluationCallbackBase;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class EvaluateExpressionTracer implements StreamTracer {
  private final XDebugSession mySession;
  private final TraceExpressionBuilder myExpressionBuilder;
  private final TraceResultInterpreter myResultInterpreter;

  public EvaluateExpressionTracer(@NotNull XDebugSession session,
                                  @NotNull TraceExpressionBuilder expressionBuilder,
                                  @NotNull TraceResultInterpreter interpreter) {
    mySession = session;
    myExpressionBuilder = expressionBuilder;
    myResultInterpreter = interpreter;
  }

  @Override
  public void trace(@NotNull StreamChain chain, @NotNull TracingCallback callback) {
    final String streamTraceExpression = myExpressionBuilder.createTraceExpression(chain);

    final XStackFrame stackFrame = mySession.getCurrentStackFrame();
    final XDebuggerEvaluator evaluator = mySession.getDebugProcess().getEvaluator();
    if (stackFrame != null && evaluator != null) {
      evaluator.evaluate(XExpressionImpl.fromText(streamTraceExpression, EvaluationMode.CODE_FRAGMENT), new XEvaluationCallbackBase() {
        @Override
        public void evaluated(@NotNull XValue result) {
          if (result instanceof JavaValue) {
            final Value reference = ((JavaValue)result).getDescriptor().getValue();
            final EvaluationContextImpl context = ((JavaValue)result).getEvaluationContext();
            final TracingResult interpretedResult = myResultInterpreter.interpret(chain, reference);
            callback.evaluated(interpretedResult, context);
            return;
          }

          callback.failed(streamTraceExpression, "Evaluation result type is unexpected");
        }

        @Override
        public void errorOccurred(@NotNull String errorMessage) {
          callback.failed(streamTraceExpression, errorMessage);
        }
      }, stackFrame.getSourcePosition());
    }
  }
}
