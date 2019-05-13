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

package com.intellij.util.xml.converters.values;

import com.intellij.util.ArrayUtil;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomBundle;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;

public class BooleanValueConverter extends ResolvingConverter<String> {
  @NonNls private static final String BOOLEAN = "boolean";

  @NonNls private static final String[] VARIANTS = {"false", "true"};

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

  @NonNls
  public String[] getAllValues() {
    final String[] strings = ArrayUtil.mergeArrays(getTrueValues(), getFalseValues());

    Arrays.sort(strings);

    return strings;
  }

  @NonNls
  public String[] getTrueValues() {
    return new String[] {"true"};
  }

  @NonNls
  public String[] getFalseValues() {
    return new String[] {"false"};
  }

  public boolean isTrue(String s) {
    return Arrays.binarySearch(getTrueValues(), s) >= 0;
  }

  @Override
  public String fromString(@Nullable @NonNls final String stringValue, final ConvertContext context) {
    if (stringValue != null && ((myAllowEmpty && stringValue.trim().length() == 0) || Arrays.binarySearch(getAllValues(), stringValue) >= 0)) {
      return stringValue;
    }
    return null;
  }

  @Override
  public String toString(@Nullable final String s, final ConvertContext context) {
    return s;
  }

  @Override
  @NotNull
  public Collection<? extends String> getVariants(final ConvertContext context) {
    return Arrays.asList(VARIANTS);
  }

  @Override
  public String getErrorMessage(@Nullable final String s, final ConvertContext context) {
    return DomBundle.message("value.converter.format.exception", s, BOOLEAN);
  }
}
