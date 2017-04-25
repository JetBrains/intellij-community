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
package com.intellij.debugger.streams.action;

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.streams.diagnostic.ex.TraceCompilationException;
import com.intellij.debugger.streams.diagnostic.ex.TraceEvaluationException;
import com.intellij.debugger.streams.psi.DebuggerPositionResolver;
import com.intellij.debugger.streams.psi.impl.AdvancedStreamChainBuilder;
import com.intellij.debugger.streams.psi.impl.DebuggerPositionResolverImpl;
import com.intellij.debugger.streams.psi.impl.StreamChainTransformerImpl;
import com.intellij.debugger.streams.resolve.ResolvedTrace;
import com.intellij.debugger.streams.trace.*;
import com.intellij.debugger.streams.trace.impl.TraceExpressionBuilderImpl;
import com.intellij.debugger.streams.trace.impl.TraceResultInterpreterImpl;
import com.intellij.debugger.streams.ui.impl.EvaluationAwareTraceWindow;
import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.debugger.streams.wrapper.StreamChainBuilder;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class TraceStreamAction extends AnAction {
  private static final Logger LOG = Logger.getInstance(TraceStreamAction.class);

  private final DebuggerPositionResolver myPositionResolver = new DebuggerPositionResolverImpl();
  private final TraceResultInterpreter myResultInterpreter = new TraceResultInterpreterImpl();
  private final StreamChainBuilder myChainBuilder = new AdvancedStreamChainBuilder(new StreamChainTransformerImpl());

  @Override
  public void update(@NotNull AnActionEvent e) {
    final XDebugSession session = getCurrentSession(e);
    final PsiElement element = session == null ? null : myPositionResolver.getNearestElementToBreakpoint(session);
    e.getPresentation().setEnabled(element != null && myChainBuilder.isChainExists(element));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final XDebugSession session = getCurrentSession(e);
    final PsiElement element = session == null ? null : myPositionResolver.getNearestElementToBreakpoint(session);
    final List<StreamChain> chains = element == null ? null : myChainBuilder.build(element);
    final StreamChain chain = chains == null || chains.isEmpty() ? null : chains.get(0);

    if (chain != null) {
      final EvaluationAwareTraceWindow window = new EvaluationAwareTraceWindow(session, chain);
      ApplicationManager.getApplication().invokeLater(window::show);
      final TraceExpressionBuilderImpl expressionBuilder = new TraceExpressionBuilderImpl(session.getProject());
      final StreamTracer tracer = new EvaluateExpressionTracer(session, expressionBuilder, myResultInterpreter);
      tracer.trace(chain, new TracingCallback() {
        @Override
        public void evaluated(@NotNull TracingResult result, @NotNull EvaluationContextImpl context) {
          final ResolvedTracingResult resolvedTrace = result.resolve();
          final List<ResolvedTrace> calls = resolvedTrace.getResolvedTraces();
          ApplicationManager.getApplication()
            .invokeLater(() -> window.setTrace(calls, result.getResult(), context));
        }

        @Override
        public void evaluationFailed(@NotNull String traceExpression, @NotNull String message) {
          notifyUI(message);
          throw new TraceEvaluationException(message, traceExpression);
        }

        @Override
        public void compilationFailed(@NotNull String traceExpression, @NotNull String message) {
          notifyUI(message);
          throw new TraceCompilationException(message, traceExpression);
        }

        private void notifyUI(@NotNull String message) {
          ApplicationManager.getApplication().invokeLater(() -> window.setFailMessage(message));
        }
      });
    }
    else {
      LOG.warn("stream chain is not built");
    }
  }

  @Nullable
  private XDebugSession getCurrentSession(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    return project == null ? null : XDebuggerManager.getInstance(project).getCurrentSession();
  }
}
