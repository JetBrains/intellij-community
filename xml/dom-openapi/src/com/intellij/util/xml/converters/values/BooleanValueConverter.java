// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.xml.converters.values;

import com.intellij.util.ArrayUtil;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.ResolvingConverter;
import com.intellij.util.xml.XmlDomBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;

public class BooleanValueConverter extends ResolvingConverter<String> {
  private static final @NonNls String BOOLEAN = "boolean";

  private static final @NonNls String[] VARIANTS = {"false", "true"};

  private final boolean myAllowEmpty;

  public static BooleanValueConverter getInstance(final boolean allowEmpty) {
     return new BooleanValueConverter(allowEmpty);
  }

  public BooleanValueConverter() {
    this(false);
  }

  public BooleanValueConverter(final boolean allowEmpty) {
    myAllowEmpty = allowEmpty;
  }

  public @NonNls String[] getAllValues() {
    final String[] strings = ArrayUtil.mergeArrays(getTrueValues(), getFalseValues());

    Arrays.sort(strings);

    return strings;
  }

  public @NonNls String[] getTrueValues() {
    return new String[] {"true"};
  }

  public @NonNls String[] getFalseValues() {
    return new String[] {"false"};
  }

  public boolean isTrue(String s) {
    return Arrays.binarySearch(getTrueValues(), s) >= 0;
  }

  @Override
  public String fromString(final @Nullable @NonNls String stringValue, final @NotNull ConvertContext context) {
    if (stringValue != null && ((myAllowEmpty && stringValue.trim().isEmpty()) || Arrays.binarySearch(getAllValues(), stringValue) >= 0)) {
      return stringValue;
    }
    return null;
  }

  @Override
  public String toString(final @Nullable String s, final @NotNull ConvertContext context) {
    return s;
  }

  @Override
  public @NotNull Collection<String> getVariants(final @NotNull ConvertContext context) {
    return Arrays.asList(VARIANTS);
  }

  @Override
  public String getErrorMessage(final @Nullable String s, final @NotNull ConvertContext context) {
    return XmlDomBundle.message("dom.converter.format.exception", s, BOOLEAN);
  }
}
