// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.converters.values;

import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.XmlDomBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CharacterValueConverter extends Converter<String> {
  private static final @NonNls String UNICODE_PREFIX = "\\u";
  private static final int UNICODE_LENGTH = 6;
  private final boolean myAllowEmpty;

  public CharacterValueConverter(final boolean allowEmpty) {
    myAllowEmpty = allowEmpty;
  }


  @Override
  public String fromString(@Nullable @NonNls String s, final @NotNull ConvertContext context) {
    if (s == null) return null;

    if (myAllowEmpty && s.trim().isEmpty()) return s;

    if (s.trim().length() == 1) return s;

    if (isUnicodeCharacterSequence(s)) {
      try {
        Integer.parseInt(s.substring(UNICODE_PREFIX.length()), 16);
        return s;
      }  catch (NumberFormatException e) {}

    }
    return null;
  }

  private static boolean isUnicodeCharacterSequence(String sequence) {
    return sequence.startsWith(UNICODE_PREFIX) && sequence.length() == UNICODE_LENGTH;
  }

  @Override
  public String toString(@Nullable String s, final @NotNull ConvertContext context) {
    return s;
  }

  @Override
  public String getErrorMessage(final @Nullable String s, final @NotNull ConvertContext context) {
   return XmlDomBundle.message("dom.converter.format.exception", s, "char");
  }
}
