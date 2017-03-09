package com.intellij.debugger.streams.trace.smart.handler;

import com.intellij.debugger.streams.trace.smart.MapToArrayTracerImpl;
import com.intellij.debugger.streams.wrapper.StreamCall;
import com.intellij.debugger.streams.wrapper.StreamCallType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class HandlerFactory {
  private static final MapToArrayTracerImpl.StreamCallTraceHandler PRODUCER_HANDLER = new ProducerHandler();
  private static final MapToArrayTracerImpl.StreamCallTraceHandler TERMINATOR_HANDLER = new TerminatorHandler();

  @NotNull
  public static MapToArrayTracerImpl.StreamCallTraceHandler create(int number, @NotNull StreamCall call) {
    if (StreamCallType.PRODUCER.equals(call.getType())) {
      return PRODUCER_HANDLER;
    }

    if (StreamCallType.TERMINATOR.equals(call.getType())) {
      return TERMINATOR_HANDLER;
    }

    final String callName = call.getName();
    switch (callName) {
      case "distinct":
        return new DistinctHandler(number);
      default:
        return new PeekTracerHandler(number, callName);
    }
  }
}
