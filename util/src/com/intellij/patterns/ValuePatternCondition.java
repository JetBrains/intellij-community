/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.patterns;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author peter
 */
public class ValuePatternCondition<T> extends PatternCondition<T>{
  private final Collection<T> myValues;

  protected ValuePatternCondition(final Collection<T> values) {
    myValues = values;
  }

  public Collection<T> getValues() {
    return myValues;
  }

  public boolean accepts(@NotNull final T t, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
    return myValues.contains(t);
  }
}