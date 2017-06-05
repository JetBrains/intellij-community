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
import com.intellij.debugger.streams.trace.TraceElement;
import com.intellij.debugger.streams.trace.TraceInfo;
import com.intellij.debugger.streams.trace.impl.TraceElementImpl;
import com.intellij.debugger.streams.trace.impl.resolve.ex.UnexpectedValueException;
import com.intellij.debugger.streams.trace.impl.resolve.ex.UnexpectedValueTypeException;
import com.intellij.debugger.streams.wrapper.StreamCall;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.BooleanValue;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;

/**
 * @author Vitaliy.Bibaev
 */
public class OptionalResolver implements CallTraceResolver {
  private final CallTraceResolver myPeekResolver = new SimplePeekCallTraceResolver();

  @NotNull
  @Override
  public TraceInfo resolve(@NotNull StreamCall call, @NotNull Value value) {
    if (value instanceof ArrayReference) {
      final Value peeksResult = ((ArrayReference)value).getValue(0);
      final TraceInfo peekInfo = myPeekResolver.resolve(call, peeksResult);
      final Map<Integer, TraceElement> before = peekInfo.getValuesOrderBefore();

      final Value optionalTrace = ((ArrayReference)value).getValue(1);
      final Value optionalValue = getOptionalValue(optionalTrace);
      if (optionalValue == null) {
        return new ValuesOrderInfo(call, before, Collections.emptyMap());
      }

      final TraceElementImpl element = new TraceElementImpl(Integer.MAX_VALUE - 1, optionalValue);
      return new ValuesOrderInfo(call, before, Collections.singletonMap(element.getTime(), element));
    }

    throw new UnexpectedValueException("trace termination with optional result must be an array value");
  }

  @Nullable
  private static Value getOptionalValue(@NotNull Value optionalTrace) {
    if (!(optionalTrace instanceof ArrayReference)) {
      throw new UnexpectedValueTypeException("optional trace must be an array value");
    }

    final ArrayReference trace = (ArrayReference)optionalTrace;
    if (!optionalIsPresent(trace)) {
      return null;
    }

    final Value value = trace.getValue(1);
    if (value instanceof ArrayReference) {
      return ((ArrayReference)value).getValue(0);
    }

    throw new UnexpectedValueTypeException("unexpected format for an optional value");
  }

  private static boolean optionalIsPresent(@NotNull ArrayReference optionalTrace) {
    final Value isPresentFlag = optionalTrace.getValue(0);
    if (isPresentFlag instanceof ArrayReference) {
      final Value isPresentValue = ((ArrayReference)isPresentFlag).getValue(0);
      if (isPresentValue instanceof BooleanValue) {
        return ((BooleanValue)isPresentValue).value();
      }
    }

    throw new UnexpectedValueTypeException("unexpected format for optional isPresent value");
  }
}
