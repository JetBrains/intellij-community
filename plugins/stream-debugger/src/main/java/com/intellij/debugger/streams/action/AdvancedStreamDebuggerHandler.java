package com.intellij.debugger.streams.action;

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.streams.resolve.ResolvedCall;
import com.intellij.debugger.streams.trace.TracingCallback;
import com.intellij.debugger.streams.trace.TracingResult;
import com.intellij.debugger.streams.trace.smart.MapToArrayTracerImpl;
import com.intellij.debugger.streams.ui.TraceWindow;
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
    new MapToArrayTracerImpl(session).trace(chain, new TracingCallback() {
      @Override
      public void evaluated(@NotNull TracingResult result, @NotNull EvaluationContextImpl context) {
        final List<ResolvedCall> calls = chain.resolveCalls(result);
        ApplicationManager.getApplication()
          .invokeLater(() -> new TraceWindow(context, session.getProject(), calls).show());
      }

      @Override
      public void failed(@NotNull String traceExpression, @NotNull String reason) {
        LOG.warn(reason + System.lineSeparator() + "expression:" + System.lineSeparator() + traceExpression);
      }
    });
  }
}
