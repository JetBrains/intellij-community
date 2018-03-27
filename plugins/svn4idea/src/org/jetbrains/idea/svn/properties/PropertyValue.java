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
package org.jetbrains.idea.svn.properties;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * TODO: Add correct support of binary properties - support in api, diff, etc.
 */
public class PropertyValue {

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
