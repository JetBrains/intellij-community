package com.intellij.debugger.streams.trace.impl;

import com.intellij.debugger.streams.trace.TraceExpressionBuilder;
import com.intellij.debugger.streams.trace.impl.handler.HandlerFactory;
import com.intellij.debugger.streams.trace.impl.handler.PeekCall;
import com.intellij.debugger.streams.trace.impl.handler.type.GenericType;
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall;
import com.intellij.debugger.streams.wrapper.ProducerStreamCall;
import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.debugger.streams.wrapper.impl.StreamChainImpl;
import com.intellij.openapi.diagnostic.Logger;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class TraceExpressionBuilderImpl implements TraceExpressionBuilder {
  private static final Logger LOG = Logger.getInstance(TraceExpressionBuilderImpl.class);

  public static final String LINE_SEPARATOR = "\n";
  private static final String RESULT_EXPRESSION = "new java.lang.Object[]{ info, streamResult, elapsedTime };" + LINE_SEPARATOR;

  @NotNull
  @Override
  public String createTraceExpression(@NotNull StreamChain chain) {
    final StreamCallTraceHandler producerHandler = HandlerFactory.create(chain.getProducerCall());
    final List<StreamCallTraceHandler> intermediateHandlers = getHandlers(chain.getIntermediateCalls());
    final StreamCallTraceHandler terminatorHandler = HandlerFactory.create(chain.getTerminationCall());

    final StreamChain traceChain = buildTraceChain(chain, producerHandler, intermediateHandlers, terminatorHandler);

    final String declarations = buildDeclarations(producerHandler, intermediateHandlers, terminatorHandler);

    final String fillingInfoArray = buildFillInfo(producerHandler, intermediateHandlers, terminatorHandler);

    final String tracingCall = buildStreamExpression(traceChain);

    final String result = declarations + tracingCall + fillingInfoArray;
    LOG.info("stream expression to trace:" + LINE_SEPARATOR + result);
    return result;
  }

  @NotNull
  private static StreamChain buildTraceChain(@NotNull StreamChain chain,
                                             @NotNull StreamCallTraceHandler producerHandler,
                                             @NotNull List<StreamCallTraceHandler> intermediateCallHandlers,
                                             @NotNull StreamCallTraceHandler terminatorHandler) {
    final List<IntermediateStreamCall> newIntermediateCalls = new ArrayList<>();
    final ProducerStreamCall producerCall = chain.getProducerCall();

    newIntermediateCalls.add(createTimePeekCall(producerCall.getTypeAfter()));
    newIntermediateCalls.addAll(producerHandler.additionalCallsAfter());

    final List<IntermediateStreamCall> intermediateCalls = chain.getIntermediateCalls();
    assert intermediateCalls.size() == intermediateCallHandlers.size();

    for (int i = 0, callCount = intermediateCallHandlers.size(); i < callCount; i++) {
      final IntermediateStreamCall call = intermediateCalls.get(i);
      final StreamCallTraceHandler handler = intermediateCallHandlers.get(i);

      newIntermediateCalls.addAll(handler.additionalCallsBefore());

      newIntermediateCalls.add(call);
      newIntermediateCalls.add(createTimePeekCall(call.getTypeAfter()));

      newIntermediateCalls.addAll(handler.additionalCallsAfter());
    }

    newIntermediateCalls.addAll(terminatorHandler.additionalCallsBefore());

    return new StreamChainImpl(producerCall, newIntermediateCalls, chain.getTerminationCall());
  }

  @NotNull
  private static IntermediateStreamCall createTimePeekCall(@NotNull GenericType elementType) {
    return new PeekCall("x -> time.incrementAndGet()", elementType);
  }

  private static String buildDeclarations(@NotNull StreamCallTraceHandler producerHandler,
                                          @NotNull List<StreamCallTraceHandler> intermediateCallsHandlers,
                                          @NotNull StreamCallTraceHandler terminatorHandler) {
    final StringBuilder builder = new StringBuilder();
    builder.append("final long startTime = java.lang.System.nanoTime();" + LINE_SEPARATOR);
    final int resultArraySize = 2 + intermediateCallsHandlers.size();
    builder.append(String.format("final java.lang.Object[] info = new java.lang.Object[%d];" + LINE_SEPARATOR, resultArraySize))
      .append("final java.util.concurrent.atomic.AtomicInteger time = new java.util.concurrent.atomic.AtomicInteger(0);")
      .append(LINE_SEPARATOR);

    builder.append(producerHandler.additionalVariablesDeclaration());
    intermediateCallsHandlers.forEach(x -> builder.append(x.additionalVariablesDeclaration()));
    builder.append(terminatorHandler.additionalVariablesDeclaration());

    return builder.toString();
  }

  @NotNull
  private static String buildStreamExpression(@NotNull StreamChain chain) {
    if (chain.getTerminationCall().isVoid()) {
      final String resultInitialization = "final Object streamResult = null;" + LINE_SEPARATOR;
      return resultInitialization + chain.getText() + ";" + LINE_SEPARATOR;
    }
    else {
      return "final Object streamResult = " + chain.getText() + ";" + LINE_SEPARATOR;
    }
  }

  @NotNull
  private static String buildFillInfo(StreamCallTraceHandler producerHandler,
                                      List<StreamCallTraceHandler> intermediateCallsHandlers,
                                      StreamCallTraceHandler terminatorHandler) {
    final StringBuilder builder = new StringBuilder();

    final Iterator<StreamCallTraceHandler> iterator =
      StreamEx.of(producerHandler).append(intermediateCallsHandlers).append(terminatorHandler).iterator();

    int i = 0;
    while (iterator.hasNext()) {
      final StreamCallTraceHandler handler = iterator.next();
      builder.append("{").append(LINE_SEPARATOR);
      builder.append(handler.prepareResult());
      builder.append(String.format("info[%d] = %s;", i, handler.getResultExpression())).append(LINE_SEPARATOR);
      builder.append("}").append(LINE_SEPARATOR);
      i++;
    }

    builder.append("final long[] elapsedTime = new long[]{ java.lang.System.nanoTime() - startTime };" + LINE_SEPARATOR);
    builder.append(RESULT_EXPRESSION);

    return builder.toString();
  }

  @NotNull
  private static List<StreamCallTraceHandler> getHandlers(@NotNull List<IntermediateStreamCall> intermediateCalls) {
    final List<StreamCallTraceHandler> result = new ArrayList<>();

    int i = 1;
    for (final IntermediateStreamCall call : intermediateCalls) {
      result.add(HandlerFactory.createIntermediate(i, call));
      i++;
    }

    return result;
  }

  public interface StreamCallTraceHandler {
    @NotNull
    String additionalVariablesDeclaration();

    @NotNull
    List<IntermediateStreamCall> additionalCallsBefore();

    @NotNull
    List<IntermediateStreamCall> additionalCallsAfter();

    @NotNull
    String prepareResult();

    @NotNull
    String getResultExpression();
  }
}
