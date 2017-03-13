/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.streams.trace;

import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.streams.remote.InvokeMethodProxy;
import com.intellij.debugger.streams.remote.RemoteMethodInvoker;
import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.intellij.xdebugger.impl.ui.tree.nodes.XEvaluationCallbackBase;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public abstract class EvaluateExpressionTracerBase implements StreamTracer {
  private final XDebugSession mySession;
  public static final String LINE_SEPARATOR = "\n";

  protected EvaluateExpressionTracerBase(@NotNull XDebugSession session) {
    mySession = session;
  }

  @Override
  public void trace(@NotNull StreamChain chain, @NotNull TracingCallback callback) {
    final String streamTraceExpression = getTraceExpression(chain);

    final XStackFrame stackFrame = mySession.getCurrentStackFrame();
    final XDebuggerEvaluator evaluator = mySession.getDebugProcess().getEvaluator();
    if (stackFrame != null && evaluator != null) {
      evaluator.evaluate(XExpressionImpl.fromText(streamTraceExpression, EvaluationMode.CODE_FRAGMENT), new XEvaluationCallbackBase() {
        @Override
        public void evaluated(@NotNull XValue result) {
          if (result instanceof JavaValue) {
            Value reference = ((JavaValue)result).getDescriptor().getValue();
            final Type type = reference.type();
            final EvaluationContextImpl context = ((JavaValue)result).getEvaluationContext();
            if (type instanceof ArrayType) {
              final DebugProcess process =
                DebuggerManager.getInstance(mySession.getProject()).getDebugProcess(mySession.getDebugProcess().getProcessHandler());
              final RemoteMethodInvoker invoker = new RemoteMethodInvoker(process, context, (ObjectReference)reference);
              final TracingResult interpretedResult = interpretResult(chain, invoker);
              callback.evaluated(interpretedResult, context);
              return;
            }
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


  @NotNull
  protected abstract String getTraceExpression(@NotNull StreamChain chain);

  @NotNull
  protected abstract TracingResult interpretResult(@NotNull StreamChain chain, @NotNull InvokeMethodProxy result);
}
