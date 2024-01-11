// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.serialization;

import com.intellij.util.xmlb.Accessor;
import com.intellij.util.xmlb.SerializationFilter;
import com.intellij.util.xmlb.SerializationFilterBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Returns true only if all filters return true
 * @author Ilya.Kazakevich
 */
public final class CompoundFilter extends SerializationFilterBase {
  private final SerializationFilter @NotNull [] myFilters;

  public CompoundFilter(final SerializationFilter @NotNull ... filters) {
    myFilters = filters.clone();
  }

  @Override
  protected boolean accepts(@NotNull final Accessor accessor, @NotNull final Object bean, @Nullable final Object beanValue) {
    for (final SerializationFilter filter : myFilters) {
      if (!filter.accepts(accessor, bean)) {
        return false;
      }
    }
    return true;
  }
}
