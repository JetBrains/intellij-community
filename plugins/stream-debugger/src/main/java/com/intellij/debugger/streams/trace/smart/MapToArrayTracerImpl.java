package com.intellij.debugger.streams.trace.smart;

import com.intellij.debugger.streams.remote.InvokeMethodProxy;
import com.intellij.debugger.streams.trace.EvaluateExpressionTracerBase;
import com.intellij.debugger.streams.trace.TracingResult;
import com.intellij.debugger.streams.trace.smart.handler.DefaultPeekInserter;
import com.intellij.debugger.streams.trace.smart.handler.DistinctCallHandler;
import com.intellij.debugger.streams.wrapper.MethodCall;
import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.xdebugger.XDebugSession;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Vitaliy.Bibaev
 */
public class MapToArrayTracerImpl extends EvaluateExpressionTracerBase {
  private static final Logger LOG = Logger.getInstance(MapToArrayTracerImpl.class);
  private static final Map<String, StreamCallTraceHandler> STREAM_CALL_HANDLERS = new HashMap<>();
  private static final StreamCallTraceHandler PEEK_INSERTER = new DefaultPeekInserter();

  static {
    STREAM_CALL_HANDLERS.put("distinct", new DistinctCallHandler());
  }

  public MapToArrayTracerImpl(@NotNull XDebugSession session) {
    super(session);
  }

  @NotNull
  @Override
  protected String getTraceExpression(@NotNull StreamChain chain) {
    final List<MethodCall> calls = chain.getCalls();
    final List<MethodCall> tracingChainCalls = new ArrayList<>();
    for (int i = 0; i < calls.size(); i++) {
      final MethodCall call = calls.get(i);
      final String name = call.getName();

      final StreamCallTraceHandler handler = STREAM_CALL_HANDLERS.getOrDefault(name, PEEK_INSERTER);
      final List<MethodCall> callsBefore = handler.callsBefore(i);
      final List<MethodCall> callsAfter = handler.callsAfter(i);

      tracingChainCalls.addAll(callsBefore);
      tracingChainCalls.add(call);
      tracingChainCalls.addAll(callsAfter);
    }

    final StreamChain newChain = new StreamChain(tracingChainCalls);
    return newChain.toString();
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
