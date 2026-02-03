// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.patterns;

import com.intellij.util.ProcessingContext;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GenericDomValuePattern<T> extends DomElementPattern<GenericDomValue<T>, GenericDomValuePattern<T>>{
  private static final InitialPatternCondition CONDITION = new InitialPatternCondition(GenericDomValue.class) {
    @Override
    public boolean accepts(final @Nullable Object o, final ProcessingContext context) {
      return o instanceof GenericDomValue;
    }
  };

  protected GenericDomValuePattern() {
    super(CONDITION);
  }

  protected GenericDomValuePattern(final Class<T> aClass) {
    super(new InitialPatternCondition(aClass) {
      @Override
      public boolean accepts(final @Nullable Object o, final ProcessingContext context) {
        return o instanceof GenericDomValue && aClass.equals(DomUtil.getGenericValueParameter(((GenericDomValue<?>)o).getDomElementType()));
      }

    });
  }

  public GenericDomValuePattern<T> withStringValue(final ElementPattern<String> pattern) {
    return with(new PatternCondition<>("withStringValue") {
      @Override
      public boolean accepts(final @NotNull GenericDomValue<T> genericDomValue, final ProcessingContext context) {
        return pattern.accepts(genericDomValue.getStringValue(), context);
      }
    });
  }

  public GenericDomValuePattern<T> withValue(final @NotNull T value) {
    return withValue(StandardPatterns.object(value));
  }

  public GenericDomValuePattern<T> withValue(final ElementPattern<?> pattern) {
    return with(new PatternCondition<>("withValue") {
      @Override
      public boolean accepts(final @NotNull GenericDomValue<T> genericDomValue, final ProcessingContext context) {
        return pattern.accepts(genericDomValue.getValue(), context);
      }
    });
  }
}
