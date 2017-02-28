package com.intellij.debugger.streams.trace.smart.resolve.impl;

import com.intellij.debugger.streams.trace.smart.resolve.TraceResolver;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class ResolverFactory {
  private ResolverFactory() {
  }

  private static class Holder {
    private static ResolverFactory INSTANCE = new ResolverFactory();
  }

  public static ResolverFactory getInstance() {
    return Holder.INSTANCE;
  }

  public TraceResolver getResolver(@NotNull String callName) {
    switch (callName) {
      case "distinct":
        return new DistinctResolver();
      default:
        return new SimplePeekResolver();
    }
  }
}
