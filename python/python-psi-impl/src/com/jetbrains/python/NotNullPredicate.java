// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.google.common.base.Predicate;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Filters out nullable elements allowing children to filter not-null elements
 *
 * @author Ilya.Kazakevich
 * @deprecated use java 8 primitives instead
 */
@Deprecated(forRemoval = true)
@ApiStatus.Internal
public class NotNullPredicate<T> implements Predicate<T> {
  /**
   * Simply filters nulls
   */
  public static final Predicate<Object> INSTANCE = new NotNullPredicate<>();

  @Override
  public final boolean apply(final @Nullable T input) {
    if (input == null) {
      return false;
    }
    return applyNotNull(input);
  }

  protected boolean applyNotNull(final @NotNull T input) {
    return true;
  }
}
