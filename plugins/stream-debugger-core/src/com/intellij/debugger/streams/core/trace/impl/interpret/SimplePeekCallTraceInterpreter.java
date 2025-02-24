// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.core.trace.impl.interpret;

import com.intellij.debugger.streams.core.trace.*;
import com.intellij.debugger.streams.core.trace.impl.TraceElementImpl;
import com.intellij.debugger.streams.core.trace.impl.interpret.ex.UnexpectedValueException;
import com.intellij.debugger.streams.core.wrapper.StreamCall;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Vitaliy.Bibaev
 */
public class SimplePeekCallTraceInterpreter implements CallTraceInterpreter {
  @Override
  public @NotNull TraceInfo resolve(@NotNull StreamCall call, @NotNull Value value) {
    if (value instanceof ArrayReference trace) {
      final Value before = trace.getValue(0);
      final Value after = trace.getValue(1);
      if (before instanceof ArrayReference && after instanceof ArrayReference) {
        final Map<Integer, TraceElement> beforeTrace = resolveTrace((ArrayReference)before);
        final Map<Integer, TraceElement> afterTrace = resolveTrace((ArrayReference)after);
        return new ValuesOrderInfo(call, beforeTrace, afterTrace);
      }
    }

    throw new UnexpectedValueException("peek operation trace is wrong format");
  }

  protected static @NotNull Map<Integer, TraceElement> resolveTrace(@NotNull ArrayReference mapArray) {
    final Value keys = mapArray.getValue(0);
    final Value values = mapArray.getValue(1);
    if (keys instanceof ArrayReference && values instanceof ArrayReference) {
      return resolveTrace((ArrayReference)keys, (ArrayReference)values);
    }

    throw new UnexpectedValueException("keys and values must be stored in arrays in peek resolver");
  }

  private static @NotNull Map<Integer, TraceElement> resolveTrace(@NotNull ArrayReference keysArray, @NotNull ArrayReference valuesArray) {
    final LinkedHashMap<Integer, TraceElement> result = new LinkedHashMap<>();
    if (keysArray.length() == valuesArray.length()) {
      for (int i = 0, size = keysArray.length(); i < size; i++) {
        final TraceElement element = resolveTraceElement(keysArray.getValue(i), valuesArray.getValue(i));
        result.put(element.getTime(), element);
      }

      return result;
    }

    throw new UnexpectedValueException("keys and values arrays should be with the same sizes");
  }

  private static @NotNull TraceElement resolveTraceElement(@NotNull Value key, @Nullable Value value) {
    if (key instanceof IntegerValue) {
      return new TraceElementImpl(((IntegerValue)key).value(), value);
    }

    throw new UnexpectedValueException("key must be an integer value");
  }
}
