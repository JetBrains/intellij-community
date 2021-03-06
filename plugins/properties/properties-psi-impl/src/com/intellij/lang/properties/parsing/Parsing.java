// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.lang.properties.parsing;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.openapi.diagnostic.Logger;

/**
 * @author Alexey
 */
public final class Parsing {
  private static final Logger LOG = Logger.getInstance(Parsing.class);

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
