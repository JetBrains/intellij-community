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
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Vitaliy.Bibaev
 */
public class ResolverFactoryImpl implements ResolverFactory {
  private static final ValuesOrderResolver EMPTY_RESOLVER = new MyEmptyResolver();

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
      case "peek":
      case "onClose":
        return new FilterResolver();
      case "flatMap":
      case "flatMapToInt":
      case "flatMapToLong":
      case "flatMapToDouble":
        return new FlatMapResolver();
      case "map":
      case "mapToInt":
      case "mapToLong":
      case "mapToDouble":
      case "mapToObj":
      case "boxed":
        return new MapResolver();
      case "sorted":
      case "toArray":
      case "collect":
        return new IdentityResolver();
      case "allMatch":
      case "anyMatch":
      case "noneMatch":
        return new AllToResultResolver();
      case "max":
      case "min":
      case "findAny":
      case "findFirst":
        return new OptionalOrderResolver();
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
}
