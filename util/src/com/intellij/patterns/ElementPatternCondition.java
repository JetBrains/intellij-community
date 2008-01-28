/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.patterns;

import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author peter
 */
public class ElementPatternCondition<T> {
  private final InitialPatternCondition<T> myInitialCondition;
  private final List<PatternCondition<? super T>> myConditions = new SmartList<PatternCondition<? super T>>();

  public ElementPatternCondition(final InitialPatternCondition<T> startCondition) {
    myInitialCondition = startCondition;
  }

  public boolean accepts(@Nullable Object o, final MatchingContext matchingContext, @NotNull TraverseContext traverseContext) {
    if (!myInitialCondition.accepts(o, matchingContext, traverseContext)) return false;
    for (final PatternCondition<? super T> condition : myConditions) {
      if (!condition.accepts((T)o, matchingContext, traverseContext)) return false;
    }
    return true;
  }

  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append(myInitialCondition);
    for (final PatternCondition<? super T> condition : myConditions) {
      builder.append(".\n  ").append(condition);
    }
    return builder.toString();
  }

  public List<PatternCondition<? super T>> getConditions() {
    return myConditions;
  }

  public InitialPatternCondition<T> getInitialCondition() {
    return myInitialCondition;
  }

  public ElementPatternCondition<T> append(PatternCondition<? super T> condition) {
    final ElementPatternCondition<T> copy = new ElementPatternCondition<T>(myInitialCondition);
    copy.myConditions.addAll(myConditions);
    copy.myConditions.add(condition);
    return copy;
  }
}
