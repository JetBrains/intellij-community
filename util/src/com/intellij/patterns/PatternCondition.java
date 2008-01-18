/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.patterns;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public abstract class PatternCondition<T> {

  public static final PatternCondition FALSE = new PatternCondition() {
    public boolean accepts(@NotNull final Object o,
                              final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
      return false;
    }

    public String toString() {
      return "FALSE";
    }
  };

  public abstract boolean accepts(@NotNull T t, final MatchingContext matchingContext, @NotNull TraverseContext traverseContext);

  @NonNls
  public String toString() {
    return super.toString();
  }
}
