// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.core.resolve;

import com.intellij.debugger.streams.core.trace.TraceElement;
import com.intellij.debugger.streams.core.trace.TraceInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Vitaliy.Bibaev
 */
public class EmptyResolver implements ValuesOrderResolver {
  @Override
  public @NotNull Result resolve(@NotNull TraceInfo info) {
    final Map<Integer, TraceElement> orderBefore = info.getValuesOrderBefore();
    final Map<Integer, TraceElement> orderAfter = info.getValuesOrderAfter();

    return Result.of(toEmptyMap(orderBefore), toEmptyMap(orderAfter));
  }

  private static @NotNull Map<TraceElement, List<TraceElement>> toEmptyMap(@NotNull Map<Integer, TraceElement> order) {
    return order.keySet().stream().collect(Collectors.toMap(order::get, x -> Collections.emptyList()));
  }
}
