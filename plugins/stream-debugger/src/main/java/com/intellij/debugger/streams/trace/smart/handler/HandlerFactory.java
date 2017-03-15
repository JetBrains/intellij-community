package com.intellij.debugger.streams.trace.smart.handler;

import com.intellij.debugger.streams.trace.smart.MapToArrayTracerImpl;
import com.intellij.debugger.streams.wrapper.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class HandlerFactory {

  @NotNull
  public static MapToArrayTracerImpl.StreamCallTraceHandler createIntermediate(int number,
                                                                               @NotNull IntermediateStreamCall call) {
    final String callName = call.getName();
    switch (callName) {
      case "distinct":
        return new DistinctHandler(number, call);
      default:
        return new PeekTracerHandler(number, callName, call.getTypeBefore(), call.getTypeAfter());
    }
  }

  public static MapToArrayTracerImpl.StreamCallTraceHandler create(@NotNull ProducerStreamCall call) {
    return new ProducerHandler(call.getTypeAfter());
  }

  public static MapToArrayTracerImpl.StreamCallTraceHandler create(@NotNull TerminatorStreamCall call) {
    return new TerminatorHandler(call.getTypeBefore());
  }
}
