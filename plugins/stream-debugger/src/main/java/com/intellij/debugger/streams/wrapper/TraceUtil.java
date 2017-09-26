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
}
