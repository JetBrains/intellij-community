package com.intellij.debugger.streams.trace.smart.resolve.impl;

import com.intellij.debugger.streams.trace.smart.resolve.CallTraceResolver;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class ResolverFactory {
  private ResolverFactory() {
  }

  private static class Holder {
    private static final ResolverFactory INSTANCE = new ResolverFactory();
  }

  public static ResolverFactory getInstance() {
    return Holder.INSTANCE;
  }

  public CallTraceResolver getResolver(@NotNull String callName) {
    switch (callName) {
      case "distinct":
        return new DistinctCallTraceResolver();
      default:
        return new SimplePeekCallTraceResolver();
    }
  }
}
