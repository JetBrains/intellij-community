/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.patterns;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author peter
 */
public class CollectionPattern<T> extends ObjectPattern<Collection<T>, CollectionPattern<T>> {
  protected CollectionPattern() {
    super(new InitialPatternCondition(Collection.class) {
      public boolean accepts(@Nullable final Object o,
                                final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return o instanceof Collection;
      }
    });
  }

  public CollectionPattern<T> all(final ElementPattern<? extends T> pattern) {
    return with(new PatternCondition<Collection<T>>() {
      public boolean accepts(@NotNull final Collection<T> collection, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        for (final T t : collection) {
          if (!pattern.getCondition().accepts(t, matchingContext, traverseContext)) return false;
        }
        return true;
      }
    });
  }

  public CollectionPattern<T> atLeastOne(final ElementPattern<? extends T> pattern) {
    return with(new PatternCondition<Collection<T>>() {
      public boolean accepts(@NotNull final Collection<T> collection, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        for (final T t : collection) {
          if (pattern.getCondition().accepts(t, matchingContext, traverseContext)) return true;
        }
        return false;
      }
    });
  }

  public CollectionPattern<T> filter(final ElementPattern<? extends T> elementPattern, final ElementPattern<Collection<T>> continuationPattern) {
    return with(new PatternCondition<Collection<T>>() {
      public boolean accepts(@NotNull final Collection<T> collection, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        List<T> filtered = new ArrayList<T>();
        for (final T t : collection) {
          if (elementPattern.getCondition().accepts(t, matchingContext, traverseContext)) {
            filtered.add(t);
          }
        }
        return continuationPattern.getCondition().accepts(filtered, matchingContext, traverseContext);
      }
    });
  }

  public CollectionPattern<T> first(final ElementPattern<? extends T> elementPattern) {
    return with(new PatternCondition<Collection<T>>() {
      public boolean accepts(@NotNull final Collection<T> collection, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return !collection.isEmpty() &&
               elementPattern.getCondition().accepts(collection.iterator().next(), matchingContext, traverseContext);
      }
    });
  }

  public CollectionPattern<T> empty() {
    return size(0);
  }

  public CollectionPattern<T> notEmpty() {
    return atLeast(1);
  }

  public CollectionPattern<T> atLeast(final int size) {
    return with(new PatternCondition<Collection<T>>() {
      public boolean accepts(@NotNull final Collection<T> ts,
                                final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return ts.size() >= size;
      }
    });
  }

  public CollectionPattern<T> size(final int size) {
    return with(new PatternCondition<Collection<T>>() {
      public boolean accepts(@NotNull final Collection<T> collection, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return size == collection.size();
      }
    });
  }

  public CollectionPattern<T> last(final ElementPattern elementPattern) {
    return with(new PatternCondition<Collection<T>>() {
      public boolean accepts(@NotNull final Collection<T> collection, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        if (collection.isEmpty()) {
          return false;
        }
        T last = null;
        for (final T t : collection) {
          last = t;
        }
        return elementPattern.getCondition().accepts(last, matchingContext, traverseContext);
      }
    });
  }
}
