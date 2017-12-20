// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.resolve;

import com.intellij.debugger.streams.trace.TraceElement;
import com.intellij.debugger.streams.trace.TraceInfo;
import com.intellij.debugger.streams.wrapper.TraceUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * @author Vitaliy.Bibaev
 */
public class IdentityResolver implements ValuesOrderResolver {
  private static final Object NULL_MARKER = new Object();
  @NotNull
  @Override
  public Result resolve(@NotNull TraceInfo info) {
    final Map<Integer, TraceElement> before = info.getValuesOrderBefore();
    final Map<Integer, TraceElement> after = info.getValuesOrderAfter();

    final Map<TraceElement, List<TraceElement>> direct = new HashMap<>();
    final Map<TraceElement, List<TraceElement>> reverse = new HashMap<>();

    final Map<Object, List<TraceElement>> grouped = StreamEx
      .of(after.keySet())
      .sorted()
      .map(after::get)
      .groupingBy(IdentityResolver::extractKey);
    final Map<Object, Integer> key2Index = new HashMap<>();

    for (final TraceElement element : before.values()) {
      final Object key = extractKey(element);

      final List<TraceElement> elements = grouped.get(key);
      if (elements == null || elements.isEmpty()) {
        direct.put(element, Collections.emptyList());
        continue;
      }

      final int nextIndex = key2Index.getOrDefault(key, -1) + 1;
      key2Index.put(key, nextIndex);
      final TraceElement afterItem = elements.get(nextIndex);

      direct.put(element, Collections.singletonList(afterItem));
      reverse.put(afterItem, Collections.singletonList(element));
    }

    return Result.of(direct, reverse);
  }

  @NotNull
  private static Object extractKey(@NotNull TraceElement element) {
    final Object key = TraceUtil.extractKey(element);
    return key == null ? NULL_MARKER : key;
  }
}
