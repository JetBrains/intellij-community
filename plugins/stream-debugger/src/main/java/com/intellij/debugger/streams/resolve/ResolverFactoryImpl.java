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

import com.intellij.debugger.streams.trace.smart.TraceElement;
import com.intellij.debugger.streams.trace.smart.resolve.TraceInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Vitaliy.Bibaev
 */
public class ResolverFactoryImpl implements ResolverFactory {
  private static final ValuesOrderResolver EMPTY_RESOLVER = new MyEmptyResolver();
  private static final ValuesOrderResolver IDENTITY_RESOLVER = new MyIdentityResolver();

  private static class Holder {
    private static final ResolverFactoryImpl INSTANCE = new ResolverFactoryImpl();
  }

  public static ResolverFactory getInstance() {
    return Holder.INSTANCE;
  }

  @NotNull
  @Override
  public ValuesOrderResolver getResolver(@NotNull String methodName) {
    switch (methodName) {
      case "filter":
      case "limit":
      case "skip":
        return new FilterResolver();
      case "flatMap":
        return new FlatMapResolver();
      case "map":
      case "mapToInt":
      case "mapToLong":
      case "mapToDouble":
      case "boxed":
        return new MapResolver();
      case "sorted":
      case "peek":
        return IDENTITY_RESOLVER;
      case "distinct":
        return new DistinctResolver();
      default:
        return EMPTY_RESOLVER;
    }
  }

  private static class MyEmptyResolver implements ValuesOrderResolver {
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

  // TODO: totally wrong. time changed => TraceElement changed too
  private static class MyIdentityResolver implements ValuesOrderResolver {
    @NotNull
    @Override
    public Result resolve(@NotNull TraceInfo info) {
      final Map<Integer, TraceElement> before = info.getValuesOrderBefore();
      final Map<Integer, TraceElement> after = info.getValuesOrderAfter();
      assert before.size() == after.size();
      return Result.of(buildIdentityMapping(before), buildIdentityMapping(after));
    }

    private static Map<TraceElement, List<TraceElement>> buildIdentityMapping(@NotNull Map<Integer, TraceElement> previousCalls) {
      final LinkedHashMap<TraceElement, List<TraceElement>> result = new LinkedHashMap<>();
      previousCalls.values().stream().distinct().forEach(x -> result.put(x, Collections.singletonList(x)));
      return result;
    }
  }
}
