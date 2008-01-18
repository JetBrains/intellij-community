/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.patterns;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class StandardPatterns {

  public static StringPattern string() {
    return new StringPattern();
  }
  
  public static CharPattern character() {
    return new CharPattern();
  }

  public static <T> ObjectPattern.Capture<T> type(Class<T> aClass) {
    return new ObjectPattern.Capture<T>(aClass);
  }

  public static <T> ElementPattern save(final Key<T> key) {
    return new ObjectPattern.Capture<T>(new NullablePatternCondition() {
      public boolean accepts(@Nullable final Object o,
                                final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        matchingContext.put(key, (T)o);
        return true;
      }
    });
  }

  public static ObjectPattern.Capture<Object> object() {
    return type(Object.class);
  }

  public static <T> ObjectPattern.Capture<T> object(@NotNull T value) {
    return type((Class<T>)value.getClass()).equalTo(value);
  }

  @SuppressWarnings({"UnusedDeclaration"})
  public static <T> CollectionPattern<T> collection(Class<T> aClass) {
    return new CollectionPattern<T>();
  }

  public static ElementPattern get(@NotNull @NonNls final String key) {
    return new ObjectPattern.Capture(new NullablePatternCondition() {
      public boolean accepts(@Nullable final Object o,
                                final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return Comparing.equal(o, matchingContext.get(key));
      }
    });
  }

  public static <T> CollectionPattern<T> collection() {
    return new CollectionPattern<T>();
  }

  public static <E> ElementPattern or(final ElementPattern... patterns) {
    return new ObjectPattern.Capture<E>(new NullablePatternCondition() {
      public boolean accepts(@Nullable final Object o,
                                final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        for (final ElementPattern pattern : patterns) {
          if (pattern.accepts(o, matchingContext, traverseContext)) return true;
        }
        return false;
      }
    });
  }

  public static <E> ElementPattern and(final ElementPattern... patterns) {
    return new ObjectPattern.Capture<E>(new NullablePatternCondition() {
      public boolean accepts(@Nullable final Object o,
                                final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        for (final ElementPattern pattern : patterns) {
          if (!pattern.accepts(o, matchingContext, traverseContext)) return false;
        }
        return true;
      }
    });
  }

  public static <E> ElementPattern not(final ElementPattern pattern) {
    return new ObjectPattern.Capture<E>(new NullablePatternCondition() {
      public boolean accepts(@Nullable final Object o,
                                final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return !pattern.accepts(o, matchingContext, traverseContext);
      }
    });
  }

  public static <T> ObjectPattern.Capture<T> optional(final ElementPattern pattern) {
    return new ObjectPattern.Capture<T>(new NullablePatternCondition() {
      public boolean accepts(@Nullable final Object o,
                                final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        pattern.accepts(o, matchingContext, traverseContext);
        return true;
      }
    });
  }

}
