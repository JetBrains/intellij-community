// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.core.trace;

import com.intellij.debugger.streams.core.lib.ResolverFactory;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public interface TracingResult {
  @NotNull
  TraceElement getResult();

  boolean exceptionThrown();

  @NotNull
  List<TraceInfo> getTrace();

  @NotNull
  ResolvedTracingResult resolve(@NotNull ResolverFactory resolverFactory);
}
