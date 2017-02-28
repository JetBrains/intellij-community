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

  private static final String INFO_ARRAY_DECLARATION_FORMAT = "final info = new Object[%f];" + System.lineSeparator();
  private static final String RETURN_EXPRESSION = "new java.lang.Object[]{ info, streamResult };" + System.lineSeparator();

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
    final StringBuilder resultPreparationBuilder = new StringBuilder();
    declarationBuilder.append(String.format(INFO_ARRAY_DECLARATION_FORMAT, callCount));
    for (int i = 0; i < calls.size(); i++) {
      final MethodCall call = calls.get(i);
      final String name = call.getName();

      final StreamCallTraceHandler handler = HandlerFactory.create(i, name);

      declarationBuilder.append(handler.additionalVariablesDeclaration());
      resultPreparationBuilder.append(handler.prepareResult());
      resultBuilder.append(String.format("additional[%d] = %s;", i, handler.getResultExpression()));

      final List<MethodCall> callsBefore = handler.additionalCallsBefore();
      final List<MethodCall> callsAfter = handler.additionalCallsAfter();

      tracingChainCalls.addAll(callsBefore);
      tracingChainCalls.add(call);
      tracingChainCalls.addAll(callsAfter);
    }

    resultBuilder.append(RETURN_EXPRESSION);
    final StreamChain newChain = new StreamChain(tracingChainCalls);
    final String tracingCall = "final Object streamResult = " + newChain.toString() + ";" + System.lineSeparator();

    final String result = declarationBuilder.toString() + tracingCall + resultPreparationBuilder.toString() + resultBuilder.toString();
    LOG.info("stream expression to trace:" + System.lineSeparator() + result);
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
