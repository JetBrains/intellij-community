// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace;

import com.intellij.debugger.streams.lib.ResolverFactory;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public interface TracingResult {
  /**
   * Returns null if and only if stream call has 'void' as return type (foreach termination call)
   */
  @NotNull
  TraceElement getResult();

  boolean exceptionThrown();

  @NotNull
  List<TraceInfo> getTrace();

  @NotNull
  ResolvedTracingResult resolve(@NotNull ResolverFactory resolverFactory);
}
