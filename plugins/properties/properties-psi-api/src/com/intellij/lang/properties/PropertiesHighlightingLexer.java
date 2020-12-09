/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.lang.properties.parsing.PropertiesLexer;
import com.intellij.lang.properties.parsing.PropertiesTokenTypes;
import com.intellij.lexer.LayeredLexer;
import com.intellij.lexer.StringLiteralLexer;
import com.intellij.psi.tree.IElementType;

import static com.intellij.psi.StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN;
import static com.intellij.psi.StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN;

public class PropertiesHighlightingLexer extends LayeredLexer{
  public PropertiesHighlightingLexer() {
    super(new PropertiesLexer());
    registerSelfStoppingLayer(new PropertiesStringLiteralLexer(PropertiesTokenTypes.VALUE_CHARACTERS),
                              new IElementType[]{PropertiesTokenTypes.VALUE_CHARACTERS},
                              IElementType.EMPTY_ARRAY);
    registerSelfStoppingLayer(new PropertiesStringLiteralLexer(PropertiesTokenTypes.KEY_CHARACTERS),
                              new IElementType[]{PropertiesTokenTypes.KEY_CHARACTERS},
                              IElementType.EMPTY_ARRAY);
  }

  /**
   * This lexer ignores the problems with escaping strings that are reported by {@link StringLiteralLexer},
   * because a backslash before a non-valid escape character is not a error.
   */
  public static final class PropertiesStringLiteralLexer extends StringLiteralLexer {

    public PropertiesStringLiteralLexer(IElementType originalLiteralToken) {
      super(StringLiteralLexer.NO_QUOTE_CHAR, originalLiteralToken, true, null);
    }

    /**
     * The method ignores the problems with invalid escape character sequence if it's not a unicode code point, because
     * the {@link java.util.Properties#load(java.io.Reader)} method does not treat a backslash character, \,
     * before a non-valid escape character as an error.
     *
     * @return current token type
     */
    @Override
    public IElementType getTokenType() {
      final IElementType tokenType = super.getTokenType();

      if (tokenType != INVALID_CHARACTER_ESCAPE_TOKEN) return tokenType;

      if (myStart + 1 >= myBuffer.length()) return tokenType;

      final char nextChar = myBuffer.charAt(myStart + 1);
      if (nextChar != 'u') return VALID_STRING_ESCAPE_TOKEN;

      return tokenType;
    }
  }
}
