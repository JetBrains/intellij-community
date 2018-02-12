// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.wrapper;

import com.intellij.debugger.streams.trace.TraceElement;
import com.intellij.openapi.util.text.StringUtil;
import com.sun.jdi.*;
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
public class TraceUtil {
  private static final String THREE_DOTS = "...";

  public static List<TraceElement> sortedByTime(@NotNull Collection<TraceElement> values) {
    return values.stream().sorted(Comparator.comparing(TraceElement::getTime)).collect(Collectors.toList());
  }

  @Nullable
  public static Object extractKey(@NotNull TraceElement element) {
    final Value value = element.getValue();
    if (!(value instanceof PrimitiveValue)) return value;
    if (value instanceof IntegerValue) return ((IntegerValue)value).value();
    if (value instanceof DoubleValue) return ((DoubleValue)value).value();
    if (value instanceof LongValue) return ((LongValue)value).value();
    if (value instanceof BooleanValue) return ((BooleanValue)value).value();
    if (value instanceof ByteValue) return ((ByteValue)value).value();
    if (value instanceof CharValue) return ((CharValue)value).value();
    if (value instanceof FloatValue) return ((FloatValue)value).value();

    throw new RuntimeException("unknown primitive value: " + value.type().name());
  }

  @NotNull
  public static String formatWithArguments(@NotNull MethodCall call) {
    return call.getName() + StreamEx.of(call.getArguments())
      .map(x -> StringUtil.shortenTextWithEllipsis(x.getText().replaceAll("\\s", ""), 30, 5, THREE_DOTS))
      .joining(", ", "(", ")");
  }

  @NotNull
  public static String formatQualifierExpression(@NotNull String expression, int maxLength) {
    expression = expression.replaceAll("\\s", "").replaceAll(",", ", ");
    if (expression.length() < maxLength) return expression;
    if (expression.isEmpty()) return "qualifier";

    return StringUtil.shortenTextWithEllipsis(expression, maxLength - 8, 5, THREE_DOTS);
  }
}
