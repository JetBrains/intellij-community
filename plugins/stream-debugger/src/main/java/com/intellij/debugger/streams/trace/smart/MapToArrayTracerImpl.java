package com.intellij.debugger.streams.trace.smart;

import com.intellij.debugger.streams.remote.InvokeMethodProxy;
import com.intellij.debugger.streams.trace.EvaluateExpressionTracerBase;
import com.intellij.debugger.streams.trace.TracingResult;
import com.intellij.debugger.streams.trace.smart.handler.HandlerFactory;
import com.intellij.debugger.streams.trace.smart.resolve.TraceInfo;
import com.intellij.debugger.streams.trace.smart.resolve.impl.ResolverFactory;
import com.intellij.debugger.streams.wrapper.MethodCall;
import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.xdebugger.XDebugSession;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class MapToArrayTracerImpl extends EvaluateExpressionTracerBase {
  private static final Logger LOG = Logger.getInstance(MapToArrayTracerImpl.class);

  private static final String RETURN_EXPRESSION = "new java.lang.Object[]{ info, streamResult };" + LINE_SEPARATOR;

  public MapToArrayTracerImpl(@NotNull XDebugSession session) {
    super(session);
  }

  public interface StreamCallTraceHandler {
    @NotNull
    String additionalVariablesDeclaration();

    @NotNull
    List<MethodCall> additionalCallsBefore();

    @NotNull
    List<MethodCall> additionalCallsAfter();

    @NotNull
    String prepareResult();

    @NotNull
    String getResultExpression();
  }

  @NotNull
  @Override
  protected String getTraceExpression(@NotNull StreamChain chain) {
    final List<MethodCall> calls = chain.getCalls();
    final List<MethodCall> tracingChainCalls = new ArrayList<>();
    final int callCount = calls.size();
    final StringBuilder declarationBuilder = new StringBuilder();
    final StringBuilder resultBuilder = new StringBuilder();
    declarationBuilder.append(String.format("final Object[] info = new Object[%d];\n", callCount))
      .append("final java.util.concurrent.atomic.AtomicInteger time = new java.util.concurrent.atomic.AtomicInteger(0);")
      .append(LINE_SEPARATOR);
    tracingChainCalls.add(calls.get(0));
    for (int i = 1; i < callCount - 1; i++) {
      final MethodCall call = calls.get(i);
      final String name = call.getName();

      final StreamCallTraceHandler handler = HandlerFactory.create(i, name);

      declarationBuilder.append(handler.additionalVariablesDeclaration());
      resultBuilder.append("{").append(LINE_SEPARATOR);
      resultBuilder.append(handler.prepareResult());
      resultBuilder.append(String.format("info[%d] = %s;", i, handler.getResultExpression())).append(LINE_SEPARATOR);
      resultBuilder.append("}").append(LINE_SEPARATOR);

      final List<MethodCall> callsBefore = handler.additionalCallsBefore();
      final List<MethodCall> callsAfter = handler.additionalCallsAfter();

      tracingChainCalls.addAll(callsBefore);
      tracingChainCalls.add(call);
      tracingChainCalls.addAll(callsAfter);
    }

    tracingChainCalls.add(calls.get(callCount - 1));
    resultBuilder.append(RETURN_EXPRESSION);
    final StreamChain newChain = new StreamChain(tracingChainCalls);
    final String tracingCall = "final Object streamResult = " + newChain.getText() + ";" + LINE_SEPARATOR;

    final String result = declarationBuilder.toString() + tracingCall + resultBuilder.toString();
    System.out.println(result);
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
      return new TracingResult() {
        @Nullable
        @Override
        public Value getResult() {
          return streamResult;
        }

        @NotNull
        @Override
        public List<TraceInfo> getTrace() {
          final int callCount = chain.length();
          final List<TraceInfo> result = new ArrayList<>(callCount);
          for (int i = 0; i < callCount; i++) {
            final String callName = chain.getCallName(i);
            final Value trackingInfo = info.getValue(i);
            final TraceInfo info = ResolverFactory.getInstance().getResolver(callName).resolve(trackingInfo);
            result.add(info);
          }

          return result;
        }
      };
    }
    else {
      throw new IllegalArgumentException("value in InvokeMethodProxy must be an ArrayReference");
    }
  }
}
