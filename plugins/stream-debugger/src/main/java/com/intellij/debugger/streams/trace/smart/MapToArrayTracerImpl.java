package com.intellij.debugger.streams.trace.smart;

import com.intellij.debugger.streams.remote.InvokeMethodProxy;
import com.intellij.debugger.streams.trace.EvaluateExpressionTracerBase;
import com.intellij.debugger.streams.trace.TracingResult;
import com.intellij.debugger.streams.trace.smart.handler.HandlerFactory;
import com.intellij.debugger.streams.trace.smart.resolve.TraceInfo;
import com.intellij.debugger.streams.trace.smart.resolve.TraceResolver;
import com.intellij.debugger.streams.trace.smart.resolve.impl.ResolverFactory;
import com.intellij.debugger.streams.trace.smart.resolve.impl.ValuesOrderInfo;
import com.intellij.debugger.streams.wrapper.StreamCall;
import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.xdebugger.XDebugSession;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class MapToArrayTracerImpl extends EvaluateExpressionTracerBase {
  private static final TraceInfo EMPTY_INFO = new ValuesOrderInfo(Collections.emptyMap());
  private static final Logger LOG = Logger.getInstance(MapToArrayTracerImpl.class);

  private static final String RETURN_EXPRESSION = "new java.lang.Object[]{ info, streamResult };" + LINE_SEPARATOR;

  public MapToArrayTracerImpl(@NotNull XDebugSession session) {
    super(session);
  }

  public interface StreamCallTraceHandler {
    @NotNull
    String additionalVariablesDeclaration();

    @NotNull
    List<StreamCall> additionalCallsBefore();

    @NotNull
    List<StreamCall> additionalCallsAfter();

    @NotNull
    String prepareResult();

    @NotNull
    String getResultExpression();
  }

  @NotNull
  @Override
  protected String getTraceExpression(@NotNull StreamChain chain) {
    final List<StreamCall> calls = chain.getCalls();
    final List<StreamCall> tracingChainCalls = new ArrayList<>();
    final int callCount = calls.size();
    final StringBuilder declarationBuilder = new StringBuilder();
    final StringBuilder resultBuilder = new StringBuilder();
    declarationBuilder.append(String.format("final Object[] info = new Object[%d];\n", callCount))
      .append("final java.util.concurrent.atomic.AtomicInteger time = new java.util.concurrent.atomic.AtomicInteger(0);")
      .append(LINE_SEPARATOR);
    tracingChainCalls.add(calls.get(0));
    for (int i = 1; i < callCount - 1; i++) {
      final StreamCall call = calls.get(i);
      final String name = call.getName();

      final StreamCallTraceHandler handler = HandlerFactory.create(i, name);

      declarationBuilder.append(handler.additionalVariablesDeclaration());
      resultBuilder.append("{").append(LINE_SEPARATOR);
      resultBuilder.append(handler.prepareResult());
      resultBuilder.append(String.format("info[%d] = %s;", i, handler.getResultExpression())).append(LINE_SEPARATOR);
      resultBuilder.append("}").append(LINE_SEPARATOR);

      final List<StreamCall> callsBefore = handler.additionalCallsBefore();
      final List<StreamCall> callsAfter = handler.additionalCallsAfter();

      tracingChainCalls.addAll(callsBefore);
      tracingChainCalls.add(call);
      tracingChainCalls.addAll(callsAfter);
    }

    tracingChainCalls.add(calls.get(callCount - 1));
    resultBuilder.append(RETURN_EXPRESSION);
    final StreamChain newChain = new StreamChain(tracingChainCalls);
    final String tracingCall = "final Object streamResult = " + newChain.getText() + ";" + LINE_SEPARATOR;

    final String result = declarationBuilder.toString() + tracingCall + resultBuilder.toString();
    LOG.info("stream expression to trace:" + LINE_SEPARATOR + result);
    return result;
  }

  @NotNull
  @Override
  protected com.intellij.debugger.streams.trace.TracingResult interpretResult(@NotNull StreamChain chain,
                                                                              @NotNull InvokeMethodProxy result) {
    final Value value = result.getValue();
    if (value instanceof ArrayReference) {
      final ArrayReference resultArray = (ArrayReference)value;
      final ArrayReference info = (ArrayReference)resultArray.getValue(0);
      final Value streamResult = resultArray.getValue(1);
      final List<TraceInfo> trace = getTrace(chain, info);
      return new MyTracingResult(streamResult, trace);
    }
    else {
      throw new IllegalArgumentException("value in InvokeMethodProxy must be an ArrayReference");
    }
  }

  @NotNull
  private List<TraceInfo> getTrace(@NotNull StreamChain chain, @NotNull ArrayReference info) {
    final int callCount = chain.length();
    final List<TraceInfo> result = new ArrayList<>(callCount);
    for (int i = 0; i < callCount; i++) {
      final String callName = chain.getCallName(i);
      final Value trace = info.getValue(i);
      final TraceResolver resolver = ResolverFactory.getInstance().getResolver(callName);
      final TraceInfo traceInfo = trace == null ? EMPTY_INFO : resolver.resolve(trace);
      result.add(traceInfo);
    }

    return result;
  }

  private static class MyTracingResult implements TracingResult {
    private final Value myStreamResult;
    private final List<TraceInfo> myTrace;

    MyTracingResult(@NotNull Value streamResult, @NotNull List<TraceInfo> trace) {
      myStreamResult = streamResult;
      myTrace = trace;
    }

    @Nullable
    @Override
    public Value getResult() {
      return myStreamResult;
    }

    @NotNull
    @Override
    public List<TraceInfo> getTrace() {
      return myTrace;
    }
  }
}
