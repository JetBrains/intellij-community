package com.intellij.debugger.streams.trace;

import com.intellij.debugger.streams.remote.InvokeMethodProxy;
import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.xdebugger.XDebugSession;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Vitaliy.Bibaev
 */
public class MapToArrayTracerImpl extends EvaluateExpressionTracerBase {
  private static final Logger LOG = Logger.getInstance(MapToArrayTracerImpl.class);

  public MapToArrayTracerImpl(@NotNull XDebugSession session) {
    super(session);
  }

  @NotNull
  @Override
  protected String getTraceExpression(@NotNull StreamChain chain) {
    return "";
  }

  @NotNull
  @Override
  protected TracingResult interpretResult(@NotNull InvokeMethodProxy result) {
    return new TracingResult() {
      @Nullable
      @Override
      public Value getResult() {
        return null;
      }

      @NotNull
      @Override
      public List<Map<Integer, Value>> getTrace() {
        return Collections.emptyList();
      }
    };
  }
}
