// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.properties;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * TODO: Add correct support of binary properties - support in api, diff, etc.
 */
public final class PropertyValue {

  @NotNull private final String myValue;

  private PropertyValue(@NotNull String propertyValue) {
    myValue = propertyValue;
  }

  @Contract(value = "null -> null; !null -> !null", pure = true)
  public static PropertyValue create(@Nullable String propertyValue) {
    return propertyValue == null ? null : new PropertyValue(propertyValue);
  }

  @Contract(value = "null -> null; !null -> !null", pure = true)
  public static String toString(@Nullable PropertyValue value) {
    return value == null ? null : value.myValue;
  }

  @Override
  public String toString() {
    return myValue;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PropertyValue value = (PropertyValue)o;

    if (!myValue.equals(value.myValue)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myValue.hashCode();
  }
}
