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
package com.intellij.debugger.streams.trace.impl.resolve;

import com.intellij.debugger.streams.trace.CallTraceResolver;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class ResolverFactory {
  private ResolverFactory() {
  }

  private static class Holder {
    private static final ResolverFactory INSTANCE = new ResolverFactory();
  }

  public static ResolverFactory getInstance() {
    return Holder.INSTANCE;
  }

  public CallTraceResolver getResolver(@NotNull String callName) {
    switch (callName) {
      case "distinct":
        return new DistinctCallTraceResolver();
      case "toArray":
      case "collect":
        return new CollectIdentityResolver();
      case "anyMatch":
        return new AnyMatchResolver();
      case "allMatch":
        return new AllMatchResolver();
      case "noneMatch":
        return new NoneMatchResolver();
      case "max":
      case "min":
      case "findAny":
      case "findFirst":
        return new OptionalResolver();
      default:
        return new SimplePeekCallTraceResolver();
    }
  }
}
