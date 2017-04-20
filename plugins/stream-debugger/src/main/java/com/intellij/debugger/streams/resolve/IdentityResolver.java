/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.streams.resolve;

import com.intellij.debugger.streams.trace.TraceElement;
import com.intellij.debugger.streams.trace.TraceInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.*;
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
  private static final Logger LOG = Logger.getInstance(IdentityResolver.class);

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

    for (final TraceElement element : before.values()) {
      final Object value = extractKey(element);

      final List<TraceElement> elements = grouped.get(value);
      final TraceElement afterItem = elements.get(0);

      direct.put(element, Collections.singletonList(afterItem));
      reverse.put(afterItem, Collections.singletonList(element));

      grouped.put(value, elements.isEmpty() ? Collections.emptyList() : elements.subList(1, elements.size()));
    }

    return Result.of(direct, reverse);
  }

  private static Object extractKey(@NotNull TraceElement element) {
    final Value value = element.getValue();
    if (!(value instanceof PrimitiveValue)) return value;
    if (value instanceof IntegerValue) return ((IntegerValue)value).value();
    if (value instanceof DoubleValue) return ((DoubleValue)value).value();
    if (value instanceof LongValue) return ((LongValue)value).value();
    if (value instanceof BooleanValue) return ((BooleanValue)value).value();
    if (value instanceof ByteValue) return ((ByteValue)value).value();
    if (value instanceof CharValue) return ((CharValue)value).value();
    if (value instanceof FloatValue) return ((FloatValue)value).value();
    
    LOG.error("unknown primitive value: " + value.type().name());
    return null;
  }
}
