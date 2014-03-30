package com.jetbrains;

import com.google.common.base.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Filters out nullable elements allowing children to filter not-null elements
 *
 * @author Ilya.Kazakevich
 */
public class NotNullPredicate<T> implements Predicate<T> {
  /**
   * Simply filters nulls
   */
  public static final Predicate<Object> INSTANCE = new NotNullPredicate<Object>();

  @Override
  public final boolean apply(@Nullable final T input) {
    if (input == null) {
      return false;
    }
    return applyNotNull(input);
  }

  protected boolean applyNotNull(@NotNull final T input) {
    return true;
  }
}
