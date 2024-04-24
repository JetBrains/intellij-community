// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml;

import com.intellij.codeInspection.util.InspectionMessage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base DOM class to convert objects of a definite type into {@link String} and back. Most often used with
 * {@link Convert} annotation with methods returning {@link GenericDomValue}&lt;T&gt;.
 *
 * @see ResolvingConverter
 * @see CustomReferenceConverter
 *
 * @param <T> Type to convert from/to.
 */
public abstract class Converter<T> {
  public abstract @Nullable T fromString(@Nullable @NonNls String s, @NotNull ConvertContext context);
  public abstract @Nullable String toString(@Nullable T t, @NotNull ConvertContext context);

  /**
   * @param s string value that couldn't be resolved
   * @param context context
   * @return error message used to highlight the errors somewhere in the UI, most often - like unresolved references in XML
   */
  public @InspectionMessage @Nullable String getErrorMessage(@Nullable String s, @NotNull ConvertContext context) {
    return XmlDomBundle.message("dom.converter.cannot.convert.default", s);
  }


  /**
   * @deprecated not necessary for Integer, use {@link com.intellij.util.xml.converters.values.NumberValueConverter}
   */
  @Deprecated
  public static final Converter<Integer> INTEGER_CONVERTER = new Converter<>() {
    @Override
    public Integer fromString(final String s, final @NotNull ConvertContext context) {
      if (s == null) return null;
      try {
        return Integer.decode(s);
      }
      catch (Exception e) {
        return null;
      }
    }

    @Override
    public String toString(final Integer t, final @NotNull ConvertContext context) {
      return t == null ? null : t.toString();
    }

    @Override
    public String getErrorMessage(final String s, final @NotNull ConvertContext context) {
      return XmlDomBundle.message("dom.converter.value.should.be.integer");
    }
  };

  /**
   * @deprecated unnecessary
   */
  @Deprecated
  public static final Converter<String> EMPTY_CONVERTER = new Converter<>() {
    @Override
    public String fromString(final String s, final @NotNull ConvertContext context) {
      return s;
    }

    @Override
    public String toString(final String t, final @NotNull ConvertContext context) {
      return t;
    }
  };

}
