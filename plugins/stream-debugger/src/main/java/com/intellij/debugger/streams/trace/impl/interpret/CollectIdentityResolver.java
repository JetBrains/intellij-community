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
package com.intellij.debugger.streams.trace.impl.interpret;

import com.intellij.debugger.streams.trace.CallTraceResolver;
import com.intellij.debugger.streams.trace.TraceElement;
import com.intellij.debugger.streams.trace.TraceInfo;
import com.intellij.debugger.streams.trace.impl.TraceElementImpl;
import com.intellij.debugger.streams.wrapper.StreamCall;
import com.sun.jdi.Value;
import one.util.streamex.IntStreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.PrimitiveIterator;

/**
 * @author Vitaliy.Bibaev
 */
public class CollectIdentityResolver implements CallTraceResolver {
  private final SimplePeekCallTraceResolver myPeekResolver = new SimplePeekCallTraceResolver();

  @NotNull
  @Override
  public TraceInfo resolve(@NotNull StreamCall call, @NotNull Value value) {
    final TraceInfo resolved = myPeekResolver.resolve(call, value);
    final Map<Integer, TraceElement> before = resolved.getValuesOrderBefore();
    final Optional<Integer> maxTime = before.keySet().stream().max(Integer::compareTo);
    if (!maxTime.isPresent()) {
      return resolved;
    }

    int timeAfter = maxTime.get() + 1;

    final PrimitiveIterator.OfInt iterator = IntStreamEx.of(before.keySet()).sorted().iterator();
    final Map<Integer, TraceElement> after = new HashMap<>(before.size());
    while (iterator.hasNext()) {
      final int timeBefore = iterator.next();

      final TraceElement elementBefore = before.get(timeBefore);
      final TraceElement elementAfter = new TraceElementImpl(timeAfter, elementBefore.getValue());

      after.put(timeAfter, elementAfter);
      ++timeAfter;
    }

    return new ValuesOrderInfo(call, before, after);
  }
}
