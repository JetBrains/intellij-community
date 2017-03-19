package com.intellij.debugger.streams.action;

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.streams.psi.DebuggerPositionResolver;
import com.intellij.debugger.streams.psi.impl.DebuggerPositionResolverImpl;
import com.intellij.debugger.streams.resolve.ResolvedTrace;
import com.intellij.debugger.streams.trace.*;
import com.intellij.debugger.streams.trace.impl.TraceExpressionBuilderImpl;
import com.intellij.debugger.streams.trace.impl.TraceResultInterpreterImpl;
import com.intellij.debugger.streams.ui.EvaluationAwareTraceWindow;
import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.debugger.streams.wrapper.StreamChainBuilder;
import com.intellij.debugger.streams.wrapper.impl.StreamChainBuilderImpl;
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
  private final TraceExpressionBuilder myExpressionBuilder = new TraceExpressionBuilderImpl();
  private final TraceResultInterpreter myResultInterpreter = new TraceResultInterpreterImpl();
  private final StreamChainBuilder myChainBuilder = new StreamChainBuilderImpl();

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
    final StreamChain chain = element == null ? null : myChainBuilder.build(element);

    if (chain != null) {
      final EvaluationAwareTraceWindow window = new EvaluationAwareTraceWindow(session.getProject(), chain);
      ApplicationManager.getApplication().invokeLater(window::show);
      final StreamTracer tracer = new EvaluateExpressionTracer(session, myExpressionBuilder, myResultInterpreter);
      tracer.trace(chain, new TracingCallback() {
        @Override
        public void evaluated(@NotNull TracingResult result, @NotNull EvaluationContextImpl context) {
          final ResolvedTracingResult resolvedTrace = result.resolve();
          final List<ResolvedTrace> calls = resolvedTrace.getResolvedTraces();
          ApplicationManager.getApplication()
            .invokeLater(() -> window.setTrace(calls, result.getResult(), context));
        }

        @Override
        public void failed(@NotNull String traceExpression, @NotNull String reason) {
          LOG.warn(reason + System.lineSeparator() + "expression:" + System.lineSeparator() + traceExpression);
          ApplicationManager.getApplication().invokeLater(() -> window.setFailMessage(reason));
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
