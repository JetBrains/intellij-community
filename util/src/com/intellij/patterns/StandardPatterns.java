/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.patterns;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
@SuppressWarnings("unchecked")
public class StandardPatterns {

  public static StringPattern string() {
    return new StringPattern();
  }
  
  public static CharPattern character() {
    return new CharPattern();
  }

  public static <T> ObjectPattern.Capture<T> instanceOf(Class<T> aClass) {
    return new ObjectPattern.Capture<T>(aClass);
  }

  public static <T> ElementPattern save(final Key<T> key) {
    return new ObjectPattern.Capture<T>(new InitialPatternCondition(Object.class) {
      public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
        context.put(key, (T)o);
        return true;
      }

      public void append(@NonNls final StringBuilder builder, final String indent) {
        builder.append("save(").append(key).append(")");
      }
    });
  }

  public static ObjectPattern.Capture<Object> object() {
    return instanceOf(Object.class);
  }

  public static <T> ObjectPattern.Capture<T> object(@NotNull T value) {
    return instanceOf((Class<T>)value.getClass()).equalTo(value);
  }

  @SuppressWarnings({"UnusedDeclaration"})
  public static <T> CollectionPattern<T> collection(Class<T> aClass) {
    return new CollectionPattern<T>();
  }

  public static ElementPattern get(@NotNull @NonNls final String key) {
    return new ObjectPattern.Capture(new InitialPatternCondition(Object.class) {
      public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
        return Comparing.equal(o, context.get(key));
      }

      public void append(@NonNls final StringBuilder builder, final String indent) {
        builder.append("get(").append(key).append(")");
      }
    });
  }

  public static <T> CollectionPattern<T> collection() {
    return new CollectionPattern<T>();
  }

  public static <E> ElementPattern<E> or(final ElementPattern<? extends E>... patterns) {
    return new ObjectPattern.Capture<E>(new InitialPatternCondition(Object.class) {
      public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
        for (final ElementPattern pattern : patterns) {
          if (pattern.getCondition().accepts(o, context)) return true;
        }
        return false;
      }

      public void append(@NonNls final StringBuilder builder, final String indent) {
        boolean first = true;
        for (final ElementPattern pattern : patterns) {
          if (!first) {
            builder.append("\n").append(indent);
          }
          first = false;
          pattern.getCondition().append(builder, indent + "  ");
        }
      }

    });
  }

  public static <E> ElementPattern and(final ElementPattern... patterns) {
    return new ObjectPattern.Capture<E>(new InitialPatternCondition(Object.class) {
      public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
        for (final ElementPattern pattern : patterns) {
          if (!pattern.getCondition().accepts(o, context)) return false;
        }
        return true;
      }

      public void append(@NonNls final StringBuilder builder, final String indent) {
        boolean first = true;
        for (final ElementPattern pattern : patterns) {
          if (!first) {
            builder.append("\n").append(indent);
          }
          first = false;
          pattern.getCondition().append(builder, indent + "  ");
        }
      }
    });
  }

  public static <E> ElementPattern not(final ElementPattern pattern) {
    return new ObjectPattern.Capture<E>(new InitialPatternCondition(Object.class) {
      public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
        return !pattern.getCondition().accepts(o, context);
      }

      public void append(@NonNls final StringBuilder builder, final String indent) {
        pattern.getCondition().append(builder.append("not("), indent + "  ");
        builder.append(")");
      }
    });
  }

  public static <T> ObjectPattern.Capture<T> optional(final ElementPattern pattern) {
    return new ObjectPattern.Capture<T>(new InitialPatternCondition(Object.class) {
      public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
        pattern.getCondition().accepts(o, context);
        return true;
      }
    });
  }

}
