/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.patterns;

import com.intellij.openapi.util.Key;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;

/**
 * @author peter
 */
public abstract class ObjectPattern<T, Self extends ObjectPattern<T, Self>> implements Cloneable, ElementPattern {
  private NullablePatternCondition myCondition;

  protected ObjectPattern(@NotNull final NullablePatternCondition condition) {
    myCondition = condition;
  }

  protected ObjectPattern(final Class<T> aClass) {
    myCondition = new NullablePatternCondition() {
      public boolean accepts(@Nullable final Object o, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return aClass.isInstance(o);
      }

      @NonNls
      public String toString() {
        return ObjectPattern.this.getClass().getSimpleName() + "(" + aClass.getName() + ")";
      }
    };
  }

  public final boolean accepts(@Nullable Object t) {
    return accepts(t, new MatchingContext(), new TraverseContext());
  }

  public final boolean accepts(@Nullable Object t, final MatchingContext matchingContext, @NotNull TraverseContext traverseContext) {
    return myCondition.accepts(t, matchingContext, traverseContext);
  }

  public Self andNot(final ElementPattern pattern) {
    return and(StandardPatterns.not(pattern));
  }

  public Self andOr(final ElementPattern... patterns) {
    return and(StandardPatterns.or(patterns));
  }

  public Self and(final ElementPattern pattern) {
    return adapt(new NullablePatternCondition() {
      public boolean accepts(@Nullable Object o, final MatchingContext matchingContext, @NotNull TraverseContext traverseContext) {
        return myCondition.accepts(o, matchingContext, traverseContext) && pattern.accepts((T)o, matchingContext, traverseContext);
      }

      @NonNls
      public String toString() {
        return "and(" + pattern.toString() + ")";
      }
    });
  }

  public Self equalTo(@NotNull final T o) {
    return with(new PatternCondition<T>() {
      public boolean accepts(@NotNull final T t, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return t.equals(o);
      }

      public String toString() {
        return "equalTo(" + o + ")";
      }
    });
  }

  @NotNull
  public Self oneOf(final T... values) {
    return oneOf(new THashSet<T>(Arrays.asList(values)));
  }

  @NotNull
  public Self oneOf(final Collection<T> set) {
    return with(new PatternCondition<T>() {
      public boolean accepts(@NotNull final T str, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return set.contains(str);
      }

      public String toString() {
        return "oneOf(" + set + ")";
      }
    });
  }

  public Self isNull() {
    return adapt(new NullablePatternCondition() {
      public boolean accepts(@Nullable final Object o, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return o == null;
      }
    });
  }

  public Self notNull() {
    return adapt(new NullablePatternCondition() {
      public boolean accepts(@Nullable final Object o, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return o != null;
      }
    });
  }

  public Self save(final Key<? super T> key) {
    return with(new PatternCondition<T>() {
      public boolean accepts(@NotNull final T t, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        matchingContext.put((Key)key, t);
        return true;
      }
    });
  }

  public Self save(final String key) {
    return with(new PatternCondition<T>() {
      public boolean accepts(@NotNull final T t, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        matchingContext.put(key, t);
        return true;
      }
    });
  }

  public Self with(final PatternCondition<? super T> pattern) {
    return adapt(new NullablePatternCondition() {
      public boolean accepts(@Nullable Object o, final MatchingContext matchingContext, @NotNull TraverseContext traverseContext) {
        return myCondition.accepts(o, matchingContext, traverseContext) && o != null && pattern.accepts((T)o, matchingContext,
                                                                                                        traverseContext);
      }

      public String toString() {
        return myCondition.toString() + "." + pattern.toString();
      }
    });
  }

  public Self without(final PatternCondition<? super T> pattern) {
    return with(new PatternCondition<T>() {
      public boolean accepts(@NotNull final T o, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return !pattern.accepts(o, matchingContext, traverseContext);
      }
    });
  }

  public Self instanceOf(final Class<?> aClass) {
    return with(new PatternCondition<T>() {
      public boolean accepts(@NotNull final T t, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return aClass.isInstance(t);
      }
    });
  }

  public String toString() {
    return myCondition.toString();
  }

  protected Self adapt(final NullablePatternCondition condition) {
    try {
      final ObjectPattern s = (ObjectPattern)clone();
      s.myCondition = condition;
      return (Self)s;
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  public static class Capture<T> extends ObjectPattern<T,Capture<T>> {

    public Capture(final Class<T> aClass) {
      super(aClass);
    }

    public Capture(@NotNull final NullablePatternCondition condition) {
      super(condition);
    }
  }

}
