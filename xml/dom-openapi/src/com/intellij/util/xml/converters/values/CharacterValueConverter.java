package com.intellij.util.xml.converters.values;

import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.DomBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public class CharacterValueConverter extends Converter<String> {
  @NonNls private static final String UNICODE_PREFIX = "\\u";
  private static final int UNICODE_LENGTH = 6;
  private final boolean myAllowEmpty;

  public CharacterValueConverter(final boolean allowEmpty) {
    myAllowEmpty = allowEmpty;
  }


  public String fromString(@Nullable @NonNls String s, final ConvertContext context) {
    if (s == null) return null;

    if (myAllowEmpty && s.trim().length() == 0) return s;

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

  public String toString(@Nullable String s, final ConvertContext context) {
    return s;
  }

  public String getErrorMessage(@Nullable final String s, final ConvertContext context) {
   return DomBundle.message("value.converter.format.exception", s, "char");
  }
}
