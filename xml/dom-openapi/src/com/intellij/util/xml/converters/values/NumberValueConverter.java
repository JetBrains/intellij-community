// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.xml.converters.values;

import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.ResolvingConverter;
import com.intellij.util.xml.XmlDomBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Collections;

public class NumberValueConverter<T extends Number> extends ResolvingConverter<T> {

  private final Class myNumberClass;
  private final boolean myAllowEmpty;

  public NumberValueConverter(final @NotNull Class<T> numberClass, final boolean allowEmpty) {
    myNumberClass = numberClass;
    myAllowEmpty = allowEmpty;
  }

  @Override
  public T fromString(final @Nullable @NonNls String s, final @NotNull ConvertContext context) {
    if (s == null) return null;

    if (myAllowEmpty && s.trim().isEmpty()) {
      return null;
    }

    //noinspection unchecked
    return (T)parseNumber(s, myNumberClass);
  }

  @Override
  public String toString(final @Nullable T value, final @NotNull ConvertContext context) {
    return value == null ? null : parseText(value, myNumberClass);
  }

  @Override
  public String getErrorMessage(final @Nullable String s, final @NotNull ConvertContext context) {
    if (s == null) return super.getErrorMessage(null, context);

    final boolean isEmpty = s.trim().isEmpty();
    if (isEmpty && myAllowEmpty) return null;

    return isEmpty ?
           XmlDomBundle.message("dom.converter.format.exception.empty.string", myNumberClass.getName()) :
           XmlDomBundle.message("dom.converter.format.exception", s, myNumberClass.getName());
  }

  @Override
  public @NotNull Collection<? extends T> getVariants(@NotNull ConvertContext context) {
    return Collections.emptySet();
  }

  public static @Nullable String parseText(@NotNull Number value, @NotNull Class targetClass) {
    if (targetClass.equals(Byte.class) || targetClass.equals(byte.class)) {
      return Byte.toString((Byte)value);
    }
    if (targetClass.equals(Short.class) || targetClass.equals(short.class)) {
      return Short.toString((Short)value);
    }
    if (targetClass.equals(Integer.class) || targetClass.equals(int.class)) {
      return Integer.toString((Integer)value);
    }
    if (targetClass.equals(Long.class) || targetClass.equals(long.class)) {
      return Long.toString((Long)value);
    }
    if (targetClass.equals(BigInteger.class)) {
      return value.toString();
    }
    if (targetClass.equals(Float.class) || targetClass.equals(float.class)) {
      return Float.toString((Float)value);
    }
    if (targetClass.equals(Double.class) || targetClass.equals(double.class)) {
      return Double.toString((Double)value);
    }
    if (targetClass.equals(BigDecimal.class) || targetClass.equals(Number.class)) {
      return ((BigDecimal)value).toPlainString();
    }
    return null;
  }

  public static @Nullable Number parseNumber(@NotNull String text, @NotNull Class targetClass) {
    try {
      String trimmed = text.trim();

      if (targetClass.equals(Byte.class) || targetClass.equals(byte.class)) {
        return Byte.decode(trimmed);
      }
      if (targetClass.equals(Short.class) || targetClass.equals(short.class)) {
        return Short.decode(trimmed);
      }
      if (targetClass.equals(Integer.class) || targetClass.equals(int.class)) {
        return Integer.decode(trimmed);
      }
      if (targetClass.equals(Long.class) || targetClass.equals(long.class)) {
        return Long.decode(trimmed);
      }
      if (targetClass.equals(BigInteger.class)) {
        return decodeBigInteger(trimmed);
      }
      if (targetClass.equals(Float.class) || targetClass.equals(float.class)) {
        return Float.valueOf(trimmed);
      }
      if (targetClass.equals(Double.class) || targetClass.equals(double.class)) {
        return Double.valueOf(trimmed);
      }
      if (targetClass.equals(BigDecimal.class) || targetClass.equals(Number.class)) {
        return new BigDecimal(trimmed);
      }
    }
    catch (NumberFormatException ex) {
      return null;
    }
    return null;
  }

  private static BigInteger decodeBigInteger(String value) {
    int radix = 10;
    int index = 0;
    boolean negative = false;

    // Handle minus sign, if present.
    if (value.startsWith("-")) {
      negative = true;
      index++;
    }

    // Handle radix specifier, if present.
    if (value.startsWith("0x", index) || value.startsWith("0X", index)) {
      index += 2;
      radix = 16;
    }
    else if (value.startsWith("#", index)) {
      index++;
      radix = 16;
    }
    else if (value.startsWith("0", index) && value.length() > 1 + index) {
      index++;
      radix = 8;
    }

    BigInteger result = new BigInteger(value.substring(index), radix);
    return (negative ? result.negate() : result);
  }
}