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
public abstract class InitialPatternCondition<T> {
  private final Class<T> myAcceptedClass;

  protected InitialPatternCondition(final Class<T> aAcceptedClass) {
    myAcceptedClass = aAcceptedClass;
  }

  public Class<T> getAcceptedClass() {
    return myAcceptedClass;
  }

  public boolean accepts(@Nullable Object o, final MatchingContext matchingContext, @NotNull TraverseContext traverseContext) {
    return myAcceptedClass.isInstance(o);
  }

  @NonNls
  public final String toString() {
    StringBuilder builder = new StringBuilder();
    append(builder, "");
    return builder.toString();
  }

  public void append(@NonNls final StringBuilder builder, final String indent) {
    builder.append("instanceOf(").append(myAcceptedClass.getSimpleName()).append(")");
  }
}
