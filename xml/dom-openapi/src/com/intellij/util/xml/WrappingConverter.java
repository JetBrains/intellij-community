// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.xml;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Allows run-time choosing of {@link Converter}.
 *
 * @author Gregory.Shrago
 */
public abstract class WrappingConverter extends Converter<Object> {

  @Override
  public Object fromString(@Nullable @NonNls String s, final @NotNull ConvertContext context) {
    final List<Converter> converters = getConverters((GenericDomValue)context.getInvocationElement());
    if (converters.isEmpty()) return s;
    for (Converter converter : converters) {
      final Object o = converter.fromString(s, context);
      if (o != null) {
        return o;
      }
    }
    return null;
  }

  @Override
  public String toString(@Nullable Object t, final @NotNull ConvertContext context) {
    final List<Converter> converters = getConverters((GenericDomValue)context.getInvocationElement());
    if (converters.isEmpty()) return String.valueOf(t);
    for (Converter converter : converters) {
      final String s = converter.toString(t, context);
      if (s != null) {
        return s;
      }
    }
    return null;
  }

  public @NotNull List<Converter> getConverters(final @NotNull GenericDomValue domElement) {
    final Converter converter = getConverter(domElement);
    return ContainerUtil.createMaybeSingletonList(converter);
  }

  public abstract @Nullable Converter getConverter(final @NotNull GenericDomValue domElement);

  public static Converter getDeepestConverter(final Converter converter, final GenericDomValue domValue) {
    Converter cur = converter;
    Converter next;
    int guard = 0;
    while (cur instanceof WrappingConverter) {
      next = ((WrappingConverter)cur).getConverter(domValue);
      if (next == null) break;
      cur = next;
      if (guard++ > 10) {
        throw new RuntimeException("Too deep wrapping for " + converter);
      }
    }
    return cur;
  }
}
