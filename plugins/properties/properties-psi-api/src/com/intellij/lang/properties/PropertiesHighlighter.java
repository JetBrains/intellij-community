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
package com.intellij.lang.properties;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.properties.parsing.PropertiesTokenTypes;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.StringEscapesTokenTypes;
import com.intellij.psi.tree.IElementType;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author max
 */
public class PropertiesHighlighter extends SyntaxHighlighterBase {
  private static final Map<IElementType, TextAttributesKey> keys1;
  private static final Map<IElementType, TextAttributesKey> keys2;

  @NotNull
  public Lexer getHighlightingLexer() {
    return new PropertiesHighlightingLexer();
  }

  public static final TextAttributesKey PROPERTY_KEY = TextAttributesKey.createTextAttributesKey(
    "PROPERTIES.KEY",
    DefaultLanguageHighlighterColors.KEYWORD
  );

  public static final TextAttributesKey PROPERTY_VALUE = TextAttributesKey.createTextAttributesKey(
    "PROPERTIES.VALUE",
    DefaultLanguageHighlighterColors.STRING
  );

  public static final TextAttributesKey PROPERTY_COMMENT = TextAttributesKey.createTextAttributesKey(
    "PROPERTIES.LINE_COMMENT",
    DefaultLanguageHighlighterColors.LINE_COMMENT
  );

  public static final TextAttributesKey PROPERTY_KEY_VALUE_SEPARATOR = TextAttributesKey.createTextAttributesKey(
    "PROPERTIES.KEY_VALUE_SEPARATOR",
    DefaultLanguageHighlighterColors.OPERATION_SIGN
  );
  public static final TextAttributesKey PROPERTIES_VALID_STRING_ESCAPE = TextAttributesKey.createTextAttributesKey(
    "PROPERTIES.VALID_STRING_ESCAPE",
    DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE
  );
  public static final TextAttributesKey PROPERTIES_INVALID_STRING_ESCAPE = TextAttributesKey.createTextAttributesKey(
    "PROPERTIES.INVALID_STRING_ESCAPE",
    DefaultLanguageHighlighterColors.INVALID_STRING_ESCAPE
  );

  static {
    keys1 = new THashMap<>();
    keys2 = new THashMap<>();

    keys1.put(PropertiesTokenTypes.VALUE_CHARACTERS, PROPERTY_VALUE);
    keys1.put(PropertiesTokenTypes.END_OF_LINE_COMMENT, PROPERTY_COMMENT);
    keys1.put(PropertiesTokenTypes.KEY_CHARACTERS, PROPERTY_KEY);
    keys1.put(PropertiesTokenTypes.KEY_VALUE_SEPARATOR, PROPERTY_KEY_VALUE_SEPARATOR);

    keys1.put(StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN, PROPERTIES_VALID_STRING_ESCAPE);
    // in fact all back-slashed escapes are allowed
    keys1.put(StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN, PROPERTIES_INVALID_STRING_ESCAPE);
    keys1.put(StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN, PROPERTIES_INVALID_STRING_ESCAPE);
  }

  @NotNull
  public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
    return SyntaxHighlighterBase.pack(keys1.get(tokenType), keys2.get(tokenType));
  }

  public static final Map<TextAttributesKey, Pair<String, HighlightSeverity>> DISPLAY_NAMES = new THashMap<>(6);
  static {
    DISPLAY_NAMES.put(PROPERTY_KEY, new Pair<>(PropertiesBundle.message("options.properties.attribute.descriptor.property.key"), null));
    DISPLAY_NAMES.put(PROPERTY_VALUE, new Pair<>(PropertiesBundle.message("options.properties.attribute.descriptor.property.value"), null));
    DISPLAY_NAMES.put(PROPERTY_KEY_VALUE_SEPARATOR,
                      new Pair<>(PropertiesBundle.message("options.properties.attribute.descriptor.key.value.separator"), null));
    DISPLAY_NAMES.put(PROPERTY_COMMENT, new Pair<>(PropertiesBundle.message("options.properties.attribute.descriptor.comment"), null));
    DISPLAY_NAMES.put(PROPERTIES_VALID_STRING_ESCAPE,
                      new Pair<>(PropertiesBundle.message("options.properties.attribute.descriptor.valid.string.escape"), null));
    DISPLAY_NAMES.put(PROPERTIES_INVALID_STRING_ESCAPE, Pair
      .create(PropertiesBundle.message("options.properties.attribute.descriptor.invalid.string.escape"), HighlightSeverity.WARNING));
  }
}
