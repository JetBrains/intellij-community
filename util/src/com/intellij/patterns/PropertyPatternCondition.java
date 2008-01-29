/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.patterns;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public abstract class PropertyPatternCondition<T,P> extends PatternCondition<T>{
  private final ElementPattern<? extends P> myPropertyPattern;

  public PropertyPatternCondition(final ElementPattern<? extends P> propertyPattern) {
    myPropertyPattern = propertyPattern;
  }

  @Nullable
  public abstract P getPropertyValue(@NotNull Object o);

  public final boolean accepts(@NotNull final T t, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
    final P value = getPropertyValue(t);
    return myPropertyPattern.getCondition().accepts(value, matchingContext, traverseContext);
  }
}
