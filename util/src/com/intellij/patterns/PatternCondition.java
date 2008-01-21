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

  public abstract boolean accepts(@NotNull T t, final MatchingContext matchingContext, @NotNull TraverseContext traverseContext);

  @NonNls
  public String toString() {
    return super.toString();
  }
}
