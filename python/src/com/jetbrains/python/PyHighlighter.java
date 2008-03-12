/*
 *  Copyright 2005 Pythonid Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS"; BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.jetbrains.python;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.SyntaxHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 28.05.2005
 * Time: 9:31:26
 * To change this template use File | Settings | File Templates.
 */
public class PyHighlighter extends SyntaxHighlighterBase {
  private static Map<IElementType, TextAttributesKey> keys1;

  @NotNull
  public Lexer getHighlightingLexer() {
    return new PythonFutureAwareLexer();
  }

  static final TextAttributesKey PY_KEYWORD =
      TextAttributesKey.createTextAttributesKey("PY.KEYWORD", SyntaxHighlighterColors.KEYWORD.getDefaultAttributes());

  static final TextAttributesKey PY_STRING =
      TextAttributesKey.createTextAttributesKey("PY.STRING", SyntaxHighlighterColors.STRING.getDefaultAttributes());
  static final TextAttributesKey PY_NUMBER =
      TextAttributesKey.createTextAttributesKey("PY.NUMBER", SyntaxHighlighterColors.NUMBER.getDefaultAttributes());

  static final TextAttributesKey PY_LINE_COMMENT =
      TextAttributesKey.createTextAttributesKey("PY.LINE_COMMENT", SyntaxHighlighterColors.LINE_COMMENT.getDefaultAttributes());

  static final TextAttributesKey PY_OPERATION_SIGN =
      TextAttributesKey.createTextAttributesKey("PY.OPERATION_SIGN", SyntaxHighlighterColors.OPERATION_SIGN.getDefaultAttributes());

  static final TextAttributesKey PY_PARENTHS =
      TextAttributesKey.createTextAttributesKey("PY.PARENTHS", SyntaxHighlighterColors.PARENTHS.getDefaultAttributes());

  static final TextAttributesKey PY_BRACKETS =
      TextAttributesKey.createTextAttributesKey("PY.BRACKETS", SyntaxHighlighterColors.BRACKETS.getDefaultAttributes());

  static final TextAttributesKey PY_BRACES =
      TextAttributesKey.createTextAttributesKey("PY.BRACES", SyntaxHighlighterColors.BRACES.getDefaultAttributes());

  static final TextAttributesKey PY_COMMA =
      TextAttributesKey.createTextAttributesKey("PY.COMMA", SyntaxHighlighterColors.COMMA.getDefaultAttributes());

  static final TextAttributesKey PY_DOT =
      TextAttributesKey.createTextAttributesKey("PY.DOT", SyntaxHighlighterColors.DOT.getDefaultAttributes());

  public static final TextAttributesKey PY_DOC_COMMENT =
      TextAttributesKey.createTextAttributesKey("PY.DOC_COMMENT", SyntaxHighlighterColors.DOC_COMMENT.getDefaultAttributes());

  public PyHighlighter() {
    keys1 = new HashMap<IElementType, TextAttributesKey>();

    fillMap(keys1, PyTokenTypes.KEYWORDS, PY_KEYWORD);
    fillMap(keys1, PyTokenTypes.OPERATIONS, PY_OPERATION_SIGN);

    keys1.put(PyTokenTypes.INTEGER_LITERAL, PY_NUMBER);
    keys1.put(PyTokenTypes.FLOAT_LITERAL, PY_NUMBER);
    keys1.put(PyTokenTypes.IMAGINARY_LITERAL, PY_NUMBER);
    keys1.put(PyTokenTypes.STRING_LITERAL, PY_STRING);

    keys1.put(PyTokenTypes.LPAR, PY_PARENTHS);
    keys1.put(PyTokenTypes.RPAR, PY_PARENTHS);

    keys1.put(PyTokenTypes.LBRACE, PY_BRACES);
    keys1.put(PyTokenTypes.RBRACE, PY_BRACES);

    keys1.put(PyTokenTypes.LBRACKET, PY_BRACKETS);
    keys1.put(PyTokenTypes.RBRACKET, PY_BRACKETS);

    keys1.put(PyTokenTypes.COMMA, PY_COMMA);
    keys1.put(PyTokenTypes.DOT, PY_DOT);

    keys1.put(PyTokenTypes.END_OF_LINE_COMMENT, PY_LINE_COMMENT);
    keys1.put(PyTokenTypes.BAD_CHARACTER, HighlighterColors.BAD_CHARACTER);
  }

  @NotNull
  public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
    return pack(keys1.get(tokenType));
  }
}
