/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.patterns;

import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class GenericDomValuePattern<T> extends DomElementPattern<GenericDomValue<T>, GenericDomValuePattern<T>>{
  private static final InitialPatternCondition CONDITION = new InitialPatternCondition(GenericDomValue.class) {
    @Override
    public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
      return o instanceof GenericDomValue;
    }
  };

  protected GenericDomValuePattern() {
    super(CONDITION);
  }

  protected GenericDomValuePattern(final Class<T> aClass) {
    super(new InitialPatternCondition(aClass) {
      @Override
      public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
        return o instanceof GenericDomValue && aClass.equals(DomUtil.getGenericValueParameter(((GenericDomValue)o).getDomElementType()));
      }

    });
  }

  public GenericDomValuePattern<T> withStringValue(final ElementPattern<String> pattern) {
    return with(new PatternCondition<GenericDomValue<T>>("withStringValue") {
      @Override
      public boolean accepts(@NotNull final GenericDomValue<T> genericDomValue, final ProcessingContext context) {
        return pattern.accepts(genericDomValue.getStringValue(), context);
      }

    });
  }

  public GenericDomValuePattern<T> withValue(@NotNull final T value) {
    return withValue(StandardPatterns.object(value));
  }

  public GenericDomValuePattern<T> withValue(final ElementPattern<?> pattern) {
    return with(new PatternCondition<GenericDomValue<T>>("withValue") {
      @Override
      public boolean accepts(@NotNull final GenericDomValue<T> genericDomValue, final ProcessingContext context) {
        return pattern.accepts(genericDomValue.getValue(), context);
      }
    });
  }
}
