/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.patterns;

import org.jetbrains.annotations.Nullable;

/**
 * @see StandardPatterns and its extenders
 *
 * @author peter
 */
public interface ElementPattern<T> {

  boolean accepts(@Nullable Object o);

  boolean accepts(@Nullable Object o, final MatchingContext matchingContext);

  ElementPatternCondition<T> getCondition();
}
