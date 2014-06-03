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
package com.intellij.util.xml;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.IdeBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * Base DOM class to convert objects of a definite type into {@link String} and back. Most often used with
 * {@link com.intellij.util.xml.Convert} annotation with methods returning {@link com.intellij.util.xml.GenericDomValue}&lt;T&gt;.
 *
 * @see com.intellij.util.xml.ResolvingConverter
 * @see com.intellij.util.xml.CustomReferenceConverter
 *
 * @param <T> Type to convert from/to.
 *
 * @author peter
 */
public abstract class Converter<T> {
  @Nullable
  public abstract T fromString(@Nullable @NonNls String s, final ConvertContext context);
  @Nullable
  public abstract String toString(@Nullable T t, final ConvertContext context);

  /**
   * @param s string value that couldn't be resolved
   * @param context context
   * @return error message used to highlight the errors somewhere in the UI, most often - like unresolved references in XML
   */
  @Nullable
  public String getErrorMessage(@Nullable String s, final ConvertContext context) {
    return CodeInsightBundle.message("error.cannot.convert.default.message", s);
  }


  /**
   * @deprecated {@link com.intellij.util.xml.converters.values.NumberValueConverter}
   */
  @Deprecated
  public static final Converter<Integer> INTEGER_CONVERTER = new Converter<Integer>() {
    @Override
    public Integer fromString(final String s, final ConvertContext context) {
      if (s == null) return null;
      try {
        return Integer.decode(s);
      }
      catch (Exception e) {
        return null;
      }
    }

    @Override
    public String toString(final Integer t, final ConvertContext context) {
      return t == null? null: t.toString();
    }

    @Override
    public String getErrorMessage(final String s, final ConvertContext context) {
      return IdeBundle.message("value.should.be.integer");
    }
  };

  @Deprecated
  public static final Converter<String> EMPTY_CONVERTER = new Converter<String>() {
    @Override
    public String fromString(final String s, final ConvertContext context) {
      return s;
    }

    @Override
    public String toString(final String t, final ConvertContext context) {
      return t;
    }

  };

}
