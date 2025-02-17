// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.core.trace.impl.interpret;

import com.intellij.debugger.streams.core.trace.*;
import com.intellij.debugger.streams.core.trace.impl.TraceElementImpl;
import com.intellij.debugger.streams.core.trace.impl.interpret.ex.UnexpectedValueException;
import com.intellij.debugger.streams.core.trace.impl.interpret.ex.UnexpectedValueTypeException;
import com.intellij.debugger.streams.core.wrapper.StreamCall;
import one.util.streamex.IntStreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.PrimitiveIterator;

/**
 * @author Vitaliy.Bibaev
 */
public class CollectIdentityTraceInterpreter implements CallTraceInterpreter {
  private final SimplePeekCallTraceInterpreter myPeekResolver = new SimplePeekCallTraceInterpreter();

  @Override
  public @NotNull TraceInfo resolve(@NotNull StreamCall call, @NotNull Value value) {
    if (!(value instanceof ArrayReference array)) {
      throw new UnexpectedValueTypeException("Array reference expected. But " + value.typeName() + " received");
    }

    final TraceInfo resolved = myPeekResolver.resolve(call, array.getValue(0));
    final Map<Integer, TraceElement> before = resolved.getValuesOrderBefore();
    if (before.isEmpty()) {
      return resolved;
    }

    int timeAfter = extractTime(array) + 1;

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

  public static int extractTime(@NotNull ArrayReference value) {
    final Value timeArray = value.getValue(1);
    if (timeArray instanceof ArrayReference) {
      final Value time = ((ArrayReference)timeArray).getValue(0);
      if (time instanceof IntegerValue) {
        return ((IntegerValue)time).value();
      }
    }

    throw new UnexpectedValueException("Could not find a maximum time value");
  }
}
