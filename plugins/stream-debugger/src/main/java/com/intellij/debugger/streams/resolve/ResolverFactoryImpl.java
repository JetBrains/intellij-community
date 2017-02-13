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

import com.intellij.openapi.util.Pair;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Vitaliy.Bibaev
 */
public class ResolverFactoryImpl implements ResolverFactory {
  private static final ValuesOrderResolver EMPTY_RESOLVER = new MyEmptyResolver();
  private static final ValuesOrderResolver IDENTITY_RESOLVER = new MyIdentityResolver();

  private static class Holder {
    private static ResolverFactoryImpl INSTANCE = new ResolverFactoryImpl();
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
        return new FilterResolver();
      case "flatMap":
        return new FlatMapResolver();
      case "map":
      case "mapToInt":
      case "mapToLong":
      case "mapToDouble":
      case "boxed":
        return new MapResolver();
      case "distinct":
        return new DistinctResolver();
      case "sorted":
      case "peek":
        return IDENTITY_RESOLVER;
      default:
        return EMPTY_RESOLVER;
    }
  }

  private static class MyEmptyResolver implements ValuesOrderResolver {
    @NotNull
    @Override
    public Pair<Map<Value, List<Value>>, Map<Value, List<Value>>> resolve(@NotNull Map<Integer, Value> previousCalls,
                                                                          @NotNull Map<Integer, Value> nextCalls) {
      return Pair.create(Collections.emptyMap(), Collections.emptyMap());
    }
  }

  private static class MyIdentityResolver implements ValuesOrderResolver {
    @NotNull
    @Override
    public Pair<Map<Value, List<Value>>, Map<Value, List<Value>>> resolve(@NotNull Map<Integer, Value> previousCalls,
                                                                          @NotNull Map<Integer, Value> nextCalls) {
      assert previousCalls.size() == nextCalls.size();
      return Pair.create(buildIdentityMapping(previousCalls), buildIdentityMapping(nextCalls));
    }

    private static Map<Value, List<Value>> buildIdentityMapping(@NotNull Map<Integer, Value> previousCalls) {
      final LinkedHashMap<Value, List<Value>> result = new LinkedHashMap<>();
      previousCalls.values().stream().distinct().forEach(x -> result.put(x, Collections.singletonList(x)));
      return result;
    }
  }
}
