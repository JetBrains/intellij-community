// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.resolve;

import com.intellij.debugger.streams.trace.TraceElement;
import com.intellij.debugger.streams.trace.TraceInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Vitaliy.Bibaev
 */
public class EmptyResolver implements ValuesOrderResolver {
  @NotNull
  @Override
  public Result resolve(@NotNull TraceInfo info) {
    final Map<Integer, TraceElement> orderBefore = info.getValuesOrderBefore();
    final Map<Integer, TraceElement> orderAfter = info.getValuesOrderAfter();

    return Result.of(toEmptyMap(orderBefore), toEmptyMap(orderAfter));
  }

  @NotNull
  private static Map<TraceElement, List<TraceElement>> toEmptyMap(@NotNull Map<Integer, TraceElement> order) {
    return order.keySet().stream().sorted().collect(Collectors.toMap(order::get, x -> Collections.emptyList()));
  }
}
