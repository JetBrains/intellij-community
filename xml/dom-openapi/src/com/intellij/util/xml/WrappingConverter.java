/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.util.xml;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Allows run-time choosing of {@link Converter}.
 *
 * @author Gregory.Shrago
 */
public abstract class WrappingConverter extends Converter<Object> {

  @Override
  public Object fromString(@Nullable @NonNls String s, final ConvertContext context) {
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
  public String toString(@Nullable Object t, final ConvertContext context) {
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

  @NotNull
  public List<Converter> getConverters(@NotNull final GenericDomValue domElement) {
    final Converter converter = getConverter(domElement);
    return converter == null ? Collections.<Converter>emptyList() : Collections.singletonList(converter);
  }

  @Nullable
  public abstract Converter getConverter(@NotNull final GenericDomValue domElement);

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
