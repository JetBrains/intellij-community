// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml;

import com.intellij.util.Function;
import com.intellij.util.ReflectionUtil;

import java.util.Objects;

public final class NamedEnumUtil {
  private static final Function<Enum, String> NAMED_SHOW = s -> ((NamedEnum) s).getValue();
  private static final Function<Enum, String> SIMPLE_SHOW = s -> s.name();

  public static <T extends Enum> T getEnumElementByValue(final Class<T> enumClass, final String value, Function<? super Enum, String> show) {
    for (final T t : enumClass.getEnumConstants()) {
      if (Objects.equals(value, show.fun(t))) {
        return t;
      }
    }
    return null;
  }
  public static <T extends Enum> T getEnumElementByValue(final Class<T> enumClass, final String value) {
    return getEnumElementByValue(enumClass, value, getShow(enumClass));
  }

  private static <T extends Enum> Function<Enum, String> getShow(final Class<T> enumClass) {
    return ReflectionUtil.isAssignable(NamedEnum.class, enumClass) ? NAMED_SHOW : SIMPLE_SHOW;
  }

  public static <T extends Enum> String getEnumValueByElement(final T element) {
    return element == null ? null : getShow(element.getClass()).fun(element);
  }

}
