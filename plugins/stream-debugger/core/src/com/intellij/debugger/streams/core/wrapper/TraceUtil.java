// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.core.wrapper;

import com.intellij.debugger.streams.core.trace.BooleanValue;
import com.intellij.debugger.streams.core.trace.ByteValue;
import com.intellij.debugger.streams.core.trace.CharValue;
import com.intellij.debugger.streams.core.trace.DoubleValue;
import com.intellij.debugger.streams.core.trace.FloatValue;
import com.intellij.debugger.streams.core.trace.IntegerValue;
import com.intellij.debugger.streams.core.trace.LongValue;
import com.intellij.debugger.streams.core.trace.PrimitiveValue;
import com.intellij.debugger.streams.core.trace.ShortValue;
import com.intellij.debugger.streams.core.trace.TraceElement;
import com.intellij.debugger.streams.core.trace.Value;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Vitaliy.Bibaev
 */
public final class TraceUtil {
  private static final String THREE_DOTS = "...";

  public static List<TraceElement> sortedByTime(@NotNull Collection<? extends TraceElement> values) {
    return values.stream().sorted(Comparator.comparing(TraceElement::getTime)).collect(Collectors.toList());
  }

  public static @Nullable Object extractKey(@NotNull TraceElement element) {
    final Value value = element.getValue();
    if (!(value instanceof PrimitiveValue)) return value;
    if (value instanceof IntegerValue integerValue) return integerValue.value();
    if (value instanceof DoubleValue doubleValue) return doubleValue.value();
    if (value instanceof LongValue longValue) return longValue.value();
    if (value instanceof BooleanValue booleanValue) return booleanValue.value();
    if (value instanceof ByteValue byteValue) return byteValue.value();
    if (value instanceof CharValue charValue) return charValue.value();
    if (value instanceof FloatValue floatValue) return floatValue.value();
    if (value instanceof ShortValue shortValue) return shortValue.value();

    throw new RuntimeException("unknown primitive value: " + value.typeName());
  }

  public static @NotNull @NlsSafe String formatWithArguments(@NotNull MethodCall call) {
    return call.getName() + call.getGenericArguments() + StreamEx.of(call.getArguments())
      .map(x -> StringUtil.shortenTextWithEllipsis(x.getText().replaceAll("\\s", ""), 30, 5, THREE_DOTS))
      .joining(", ", "(", ")");
  }

  public static @NotNull @NlsSafe String formatQualifierExpression(@NotNull String expression, int maxLength) {
    expression = expression.replaceAll("\\s", "").replaceAll(",", ", ");
    if (expression.length() < maxLength) return expression;
    if (expression.isEmpty()) return "qualifier";

    return StringUtil.shortenTextWithEllipsis(expression, maxLength - 8, 5, THREE_DOTS);
  }
}
