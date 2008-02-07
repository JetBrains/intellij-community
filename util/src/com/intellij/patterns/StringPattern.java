/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.patterns;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

import com.intellij.util.ProcessingContext;

/**
 * @author peter
 */
public class StringPattern extends ObjectPattern<String, StringPattern> {
  protected StringPattern() {
    super(new InitialPatternCondition<String>(String.class) {
      public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
        return o instanceof String;
      }


      public void append(@NonNls final StringBuilder builder, final String indent) {
        builder.append("string()");
      }
    });
  }

  @NotNull
  public StringPattern startsWith(@NonNls @NotNull final String s) {
    return with(new PatternCondition<String>("startsWith") {
      public boolean accepts(@NotNull final String str, final ProcessingContext context) {
        return str.startsWith(s);
      }
    });
  }

  @NotNull
  public StringPattern endsWith(@NonNls @NotNull final String s) {
    return with(new PatternCondition<String>("endsWith") {
      public boolean accepts(@NotNull final String str, final ProcessingContext context) {
        return str.endsWith(s);
      }
    });
  }
  @NotNull
  public StringPattern contains(@NonNls @NotNull final String s) {
    return with(new PatternCondition<String>("contains") {
      public boolean accepts(@NotNull final String str, final ProcessingContext context) {
        return str.contains(s);
      }

    });
  }

  @NotNull
  public StringPattern contains(@NonNls @NotNull final ElementPattern pattern) {
    return with(new PatternCondition<String>("contains") {
      public boolean accepts(@NotNull final String str, final ProcessingContext context) {
        for (int i = 0; i < str.length(); i++) {
          if (pattern.accepts(str.charAt(i))) return true;
        }
        return false;
      }
    });
  }

  public StringPattern longerThan(final int minLength) {
    return with(new PatternCondition<String>("longerThan") {
      public boolean accepts(@NotNull final String s, final ProcessingContext context) {
        return s.length() > minLength;
      }
    });
  }

  @NotNull
  public StringPattern oneOf(@NonNls final String... values) {
    return super.oneOf(values);
  }

  @NotNull
  public StringPattern oneOfIgnoreCase(@NonNls final String... values) {
    return with(new CaseInsensitiveValuePatternCondition("oneOfIgnoreCase", values));
  }

  @NotNull
  public StringPattern oneOf(@NonNls final Collection<String> set) {
    return super.oneOf(set);
  }

}
