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

import com.intellij.debugger.streams.resolve.impl.StdLibResolverFactory;
import com.intellij.debugger.streams.resolve.impl.StreamExResolverFactoryImpl;
import com.intellij.debugger.streams.trace.TraceElement;
import com.intellij.debugger.streams.trace.TraceInfo;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Vitaliy.Bibaev
 */
public class ResolverFactoryImpl implements ResolverFactory.StrongFactory {
  private static final ValuesOrderResolver EMPTY_RESOLVER = new MyEmptyResolver();

  private final List<ResolverFactory> myFactories;

  private ResolverFactoryImpl(@NotNull ResolverFactory... factories) {
    super();
    myFactories = new ArrayList<>(Arrays.asList(factories));
  }

  private static class Holder {
    private static final ResolverFactoryImpl INSTANCE =
      new ResolverFactoryImpl(new StdLibResolverFactory(), new StreamExResolverFactoryImpl());
  }

  public static ResolverFactory.StrongFactory getInstance() {
    return Holder.INSTANCE;
  }

  @NotNull
  @Override
  public ValuesOrderResolver getResolver(@NotNull String methodName) {
    for (final ResolverFactory factory : myFactories) {
      final ValuesOrderResolver resolver = factory.getResolver(methodName);
      if (resolver != null) {
        return resolver;
      }
    }

    return EMPTY_RESOLVER;
  }

  private static ValuesOrderResolver tryToGetExtensionResolver(@NotNull String methodName) {
    return EMPTY_RESOLVER;
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
