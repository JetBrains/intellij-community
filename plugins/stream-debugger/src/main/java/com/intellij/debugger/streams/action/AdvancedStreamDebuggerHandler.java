package com.intellij.debugger.streams.action;

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.streams.resolve.ResolvedTrace;
import com.intellij.debugger.streams.trace.TracingCallback;
import com.intellij.debugger.streams.trace.TracingResult;
import com.intellij.debugger.streams.trace.smart.MapToArrayTracerImpl;
import com.intellij.debugger.streams.ui.EvaluationAwareTraceWindow;
import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.xdebugger.XDebugSession;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class AdvancedStreamDebuggerHandler extends JvmStreamDebuggerActionHandler {
  private static final Logger LOG = Logger.getInstance(AdvancedStreamDebuggerHandler.class);

  @Override
  public void handle(@NotNull XDebugSession session, @NotNull StreamChain chain) {
    final EvaluationAwareTraceWindow window = new EvaluationAwareTraceWindow(session.getProject(), chain);
    ApplicationManager.getApplication().invokeLater(window::show);
    new MapToArrayTracerImpl(session).trace(chain, new TracingCallback() {
      @Override
      public void evaluated(@NotNull TracingResult result, @NotNull EvaluationContextImpl context) {
        final List<ResolvedTrace> calls = resolve(result.getTrace());
        ApplicationManager.getApplication()
          .invokeLater(() -> window.setTrace(calls, result.getResult(), context));
      }

      @Override
      public void failed(@NotNull String traceExpression, @NotNull String reason) {
        LOG.warn(reason + System.lineSeparator() + "expression:" + System.lineSeparator() + traceExpression);
        ApplicationManager.getApplication().invokeLater(window::setFailMessage);
      }
    });
  }
}
