/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.patterns;

import com.intellij.openapi.util.Key;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * @author peter
 */
public abstract class ObjectPattern<T, Self extends ObjectPattern<T, Self>> implements Cloneable, ElementPattern<T> {
  private ElementPatternCondition<T> myCondition;

  protected ObjectPattern(@NotNull final InitialPatternCondition<T> condition) {
    myCondition = new ElementPatternCondition<T>(condition);
  }

  protected ObjectPattern(final Class<T> aClass) {
    myCondition = new ElementPatternCondition<T>(new InitialPatternCondition<T>(aClass) {
      public boolean accepts(@Nullable final Object o, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return aClass.isInstance(o);
      }
    });
  }

  public final boolean accepts(@Nullable Object t) {
    return myCondition.accepts(t, new MatchingContext(), new TraverseContext());
  }

  public boolean accepts(@Nullable final Object o, final MatchingContext matchingContext) {
    return myCondition.accepts(o, matchingContext, new TraverseContext());
  }

  public final ElementPatternCondition getCondition() {
    return myCondition;
  }

  public Self andNot(final ElementPattern pattern) {
    return and(StandardPatterns.not(pattern));
  }

  public Self andOr(final ElementPattern... patterns) {
    return and(StandardPatterns.or(patterns));
  }

  public Self and(final ElementPattern pattern) {
    return with(new PatternCondition<T>("and") {
      public boolean accepts(@NotNull final T t, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return pattern.getCondition().accepts(t, matchingContext, traverseContext);
      }
    });
  }

  public Self equalTo(@NotNull final T o) {
    return with(new ValuePatternCondition<T>("equalTo", Collections.singleton(o)) {
      public boolean accepts(@NotNull final T t, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return t.equals(o);
      }
    });
  }

  @NotNull
  public Self oneOf(final T... values) {
    return oneOf(new THashSet<T>(Arrays.asList(values)));
  }

  @NotNull
  public Self oneOf(final Collection<T> set) {
    return with(new ValuePatternCondition<T>("oneOf", set));
  }

  public Self isNull() {
    return adapt(new ElementPatternCondition<T>(new InitialPatternCondition(Object.class) {
      public boolean accepts(@Nullable final Object o,
                             final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return o == null;
      }
    }));
  }

  public Self notNull() {
    return adapt(new ElementPatternCondition<T>(new InitialPatternCondition(Object.class) {
      public boolean accepts(@Nullable final Object o, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return o != null;
      }
    }));
  }

  public Self save(final Key<? super T> key) {
    return with(new PatternCondition<T>("save") {
      public boolean accepts(@NotNull final T t, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        matchingContext.put((Key)key, t);
        return true;
      }
    });
  }

  public Self save(final String key) {
    return with(new PatternCondition<T>("save") {
      public boolean accepts(@NotNull final T t, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        matchingContext.put(key, t);
        return true;
      }
    });
  }

  public Self with(final PatternCondition<? super T> pattern) {
    final ElementPatternCondition<T> condition = myCondition.append(pattern);
    return adapt(condition);
  }

  private Self adapt(final ElementPatternCondition<T> condition) {
    try {
      final ObjectPattern s = (ObjectPattern)clone();
      s.myCondition = condition;
      return (Self)s;
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  public Self without(final PatternCondition<? super T> pattern) {
    return with(new PatternCondition<T>("without") {
      public boolean accepts(@NotNull final T o, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return !pattern.accepts(o, matchingContext, traverseContext);
      }
    });
  }

  public String toString() {
    return myCondition.toString();
  }

  public static class Capture<T> extends ObjectPattern<T,Capture<T>> {

    public Capture(final Class<T> aClass) {
      super(aClass);
    }

    public Capture(@NotNull final InitialPatternCondition<T> condition) {
      super(condition);
    }
  }

}
