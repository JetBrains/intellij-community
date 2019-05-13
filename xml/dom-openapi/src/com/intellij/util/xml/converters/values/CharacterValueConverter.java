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


  @Override
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

  @Override
  public String toString(@Nullable String s, final ConvertContext context) {
    return s;
  }

  @Override
  public String getErrorMessage(@Nullable final String s, final ConvertContext context) {
   return DomBundle.message("value.converter.format.exception", s, "char");
  }
}
