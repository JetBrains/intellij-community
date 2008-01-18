/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.patterns;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
*/
public abstract class NullablePatternCondition {
  public static final NullablePatternCondition TRUE = new NullablePatternCondition() {
    public boolean accepts(@Nullable final Object o, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
      return true;
    }

    public String toString() {
      return "";
    }
  };

  public abstract boolean accepts(@Nullable Object o, final MatchingContext matchingContext, @NotNull TraverseContext traverseContext);

  @NonNls
  public String toString() {
    return super.toString();
  }
}
