// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.impl.interpret;

import com.intellij.debugger.streams.trace.CallTraceInterpreter;
import com.intellij.debugger.streams.trace.TraceElement;
import com.intellij.debugger.streams.trace.TraceInfo;
import com.intellij.debugger.streams.trace.impl.TraceElementImpl;
import com.intellij.debugger.streams.trace.impl.interpret.ex.UnexpectedValueException;
import com.intellij.debugger.streams.wrapper.StreamCall;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.IntegerValue;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Vitaliy.Bibaev
 */
public class SimplePeekCallTraceInterpreter implements CallTraceInterpreter {
  @NotNull
  @Override
  public TraceInfo resolve(@NotNull StreamCall call, @NotNull Value value) {
    if (value instanceof ArrayReference) {
      final ArrayReference trace = (ArrayReference)value;
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

  @NotNull
  private static Map<Integer, TraceElement> resolveTrace(@NotNull ArrayReference mapArray) {
    final Value keys = mapArray.getValue(0);
    final Value values = mapArray.getValue(1);
    if (keys instanceof ArrayReference && values instanceof ArrayReference) {
      return resolveTrace((ArrayReference)keys, (ArrayReference)values);
    }

    throw new UnexpectedValueException("keys and values must be stored in arrays in peek resolver");
  }

  @NotNull
  private static Map<Integer, TraceElement> resolveTrace(@NotNull ArrayReference keysArray, @NotNull ArrayReference valuesArray) {
    final LinkedHashMap<Integer, TraceElement> result = new LinkedHashMap<>();
    final List<Value> keyMirrors = keysArray.getValues();
    final List<Value> valueMirrors = valuesArray.getValues();
    if (keyMirrors.size() == valueMirrors.size()) {
      for (int i = 0, size = keyMirrors.size(); i < size; i++) {
        final TraceElement element = resolveTraceElement(keyMirrors.get(i), valueMirrors.get(i));
        result.put(element.getTime(), element);
      }

      return result;
    }

    throw new UnexpectedValueException("keys and values arrays should be with the same sizes");
  }

  @NotNull
  private static TraceElement resolveTraceElement(@NotNull Value key, @Nullable Value value) {
    if (key instanceof IntegerValue) {
      return new TraceElementImpl(((IntegerValue)key).value(), value);
    }

    throw new UnexpectedValueException("key must be an integer value");
  }
}
