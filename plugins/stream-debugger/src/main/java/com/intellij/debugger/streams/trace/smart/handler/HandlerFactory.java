package com.intellij.debugger.streams.trace.smart.handler;

import com.intellij.debugger.streams.trace.smart.MapToArrayTracerImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class HandlerFactory {
  @NotNull
  public static MapToArrayTracerImpl.StreamCallTraceHandler create(int number, @NotNull String name) {
    switch (name) {
      case "distinct":
        return new DistinctHandler(number, name);
      default:
        return new PeekTracerHandler(number, name);
    }
  }
}
