package com.intellij.debugger.streams.trace.smart.handler;

import com.intellij.debugger.streams.trace.smart.MapToArrayTracerImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class HandlerFactory {
  private static final MapToArrayTracerImpl.StreamCallTraceHandler PRODUCER_HANDLER = new ProducerHandler();

  public static MapToArrayTracerImpl.StreamCallTraceHandler create(int number, @NotNull String name) {
    if (number == 0) {
      return PRODUCER_HANDLER;
    }

    switch (name) {
      case "distinct":
        return new DistinctHandler(number, name);
      default:
        return new PeekTracerHandler(number, name);
    }
  }
}
