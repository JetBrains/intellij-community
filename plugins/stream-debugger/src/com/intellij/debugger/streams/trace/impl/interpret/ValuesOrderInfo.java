// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.impl.interpret;

import com.intellij.debugger.streams.trace.TraceElement;
import com.intellij.debugger.streams.trace.TraceInfo;
import com.intellij.debugger.streams.wrapper.StreamCall;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Vitaliy.Bibaev
 */
public class ValuesOrderInfo implements TraceInfo {
  private final StreamCall myStreamCall;
  private final Map<Integer, TraceElement> myValuesOrderAfter;
  private final Map<Integer, TraceElement> myValuesOrderBefore;

  ValuesOrderInfo(@NotNull StreamCall call, @NotNull Map<Integer, TraceElement> before, @NotNull Map<Integer, TraceElement> after) {
    myStreamCall = call;
    myValuesOrderBefore = before;
    myValuesOrderAfter = after;
  }

  @Override
  public @NotNull StreamCall getCall() {
    return myStreamCall;
  }

  @Override
  public @NotNull Map<Integer, TraceElement> getValuesOrderBefore() {
    return myValuesOrderBefore;
  }

  @Override
  public @NotNull Map<Integer, TraceElement> getValuesOrderAfter() {
    return myValuesOrderAfter;
  }

  @Override
  public @Nullable Map<TraceElement, List<TraceElement>> getDirectTrace() {
    return null;
  }

  @Override
  public @Nullable Map<TraceElement, List<TraceElement>> getReverseTrace() {
    return null;
  }

  public static TraceInfo empty(@NotNull StreamCall call) {
    return new ValuesOrderInfo(call, Collections.emptyMap(), Collections.emptyMap());
  }
}
