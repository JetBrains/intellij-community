/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG.compact;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.SyntaxHighlighterColors;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import org.intellij.plugins.relaxNG.compact.lexer.CompactSyntaxLexerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 04.08.2007
 */
public class RncHighlighter extends SyntaxHighlighterBase {
  @NotNull
  public Lexer getHighlightingLexer() {
    return new CompactSyntaxLexerAdapter();
  }

  private static final Map<IElementType, TextAttributesKey> ourMap1;

  static {
    ourMap1 = new HashMap<IElementType, TextAttributesKey>();

    fillMap(ourMap1, RncTokenTypes.KEYWORDS, SyntaxHighlighterColors.KEYWORD);
    fillMap(ourMap1, RncTokenTypes.OPERATORS, SyntaxHighlighterColors.OPERATION_SIGN);

    fillMap(ourMap1, RncTokenTypes.STRINGS, SyntaxHighlighterColors.STRING);

    ourMap1.put(RncTokenTypes.LBRACE, SyntaxHighlighterColors.BRACES);
    ourMap1.put(RncTokenTypes.RBRACE, SyntaxHighlighterColors.BRACES);

    ourMap1.put(RncTokenTypes.LBRACKET, SyntaxHighlighterColors.BRACKETS);
    ourMap1.put(RncTokenTypes.RBRACKET, SyntaxHighlighterColors.BRACKETS);

    ourMap1.put(RncTokenTypes.LPAREN, SyntaxHighlighterColors.PARENTHS);
    ourMap1.put(RncTokenTypes.RPAREN, SyntaxHighlighterColors.PARENTHS);

    ourMap1.put(RncTokenTypes.COMMA, SyntaxHighlighterColors.COMMA);

    fillMap(ourMap1, RncTokenTypes.DOC_TOKENS, SyntaxHighlighterColors.DOC_COMMENT);
    fillMap(ourMap1, RncTokenTypes.COMMENTS, SyntaxHighlighterColors.LINE_COMMENT);

    fillMap(ourMap1, RncTokenTypes.IDENTIFIERS, CodeInsightColors.LOCAL_VARIABLE_ATTRIBUTES);

    ourMap1.put(RncTokenTypes.ILLEGAL_CHAR, HighlighterColors.BAD_CHARACTER);
  }

  @NotNull
  public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
    return pack(ourMap1.get(tokenType));
  }
}