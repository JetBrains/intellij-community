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

/**
 * @author Alexey
 */
package com.intellij.lang.properties.parsing;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.openapi.diagnostic.Logger;

public class Parsing {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.properties.parsing.Parsing");

  public static void parseProperty(PsiBuilder builder) {
    if (builder.getTokenType() == PropertiesTokenTypes.KEY_CHARACTERS) {
      final PsiBuilder.Marker prop = builder.mark();

      parseKey(builder);
      if (builder.getTokenType() == PropertiesTokenTypes.KEY_VALUE_SEPARATOR) {
        parseKeyValueSeparator(builder);
      }
      if (builder.getTokenType() == PropertiesTokenTypes.VALUE_CHARACTERS) {
        parseValue(builder);
      }
      prop.done(PropertiesElementTypes.PROPERTY);
    }
    else {
      builder.advanceLexer();
      builder.error(PropertiesBundle.message("property.key.expected.parsing.error.message"));
    }
  }

  private static void parseKeyValueSeparator(final PsiBuilder builder) {
    LOG.assertTrue(builder.getTokenType() == PropertiesTokenTypes.KEY_VALUE_SEPARATOR);
    builder.advanceLexer();
  }

  private static void parseValue(final PsiBuilder builder) {
    if (builder.getTokenType() == PropertiesTokenTypes.VALUE_CHARACTERS) {
      builder.advanceLexer();
    }
  }

  private static void parseKey(final PsiBuilder builder) {
    LOG.assertTrue(builder.getTokenType() == PropertiesTokenTypes.KEY_CHARACTERS);
    builder.advanceLexer();
  }
}
