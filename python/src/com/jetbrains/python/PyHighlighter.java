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
import static com.intellij.openapi.editor.SyntaxHighlighterColors.*;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.lexer.PythonFutureAwareLexer;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
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

  private static TextAttributesKey _copy(String name, TextAttributesKey src) {
    return TextAttributesKey.createTextAttributesKey(name, src.getDefaultAttributes().clone());
  }

  static final TextAttributesKey PY_KEYWORD = _copy("PY.KEYWORD", KEYWORD);

  static final TextAttributesKey PY_STRING = _copy("PY.STRING", STRING);
  static final TextAttributesKey PY_NUMBER = _copy("PY.NUMBER", NUMBER);

  static final TextAttributesKey PY_LINE_COMMENT = _copy("PY.LINE_COMMENT", LINE_COMMENT);

  static final TextAttributesKey PY_OPERATION_SIGN = _copy("PY.OPERATION_SIGN", OPERATION_SIGN);

  static final TextAttributesKey PY_PARENTHS = _copy("PY.PARENTHS", PARENTHS);

  static final TextAttributesKey PY_BRACKETS = _copy("PY.BRACKETS", BRACKETS);

  static final TextAttributesKey PY_BRACES = _copy("PY.BRACES", BRACES);

  static final TextAttributesKey PY_COMMA = _copy("PY.COMMA", COMMA);

  static final TextAttributesKey PY_DOT = _copy("PY.DOT", DOT);

  public static final TextAttributesKey PY_DOC_COMMENT = _copy("PY.DOC_COMMENT", DOC_COMMENT);

  public static final TextAttributesKey PY_DECORATOR = TextAttributesKey.createTextAttributesKey(
    "PY.DECORATOR", new TextAttributes(Color.blue.darker(), null, null, null, Font.PLAIN)
  );

  public static final TextAttributesKey PY_CLASS_DEFINITION = TextAttributesKey.createTextAttributesKey(
    "PY.CLASS_DEFINITION", new TextAttributes(Color.black, null, null, null, Font.BOLD)
  );

  public static final TextAttributesKey PY_FUNC_DEFINITION = TextAttributesKey.createTextAttributesKey(
    "PY.FUNC_DEFINITION", new TextAttributes(Color.black, null, null, null, Font.BOLD)
  );

  public static final TextAttributesKey PY_PREDEFINED_DEFINITION = TextAttributesKey.createTextAttributesKey(
    "PY.PREDEFINED_DEFINITION", new TextAttributes(Color.magenta.darker(), null, null, null, Font.BOLD)
  );

  public static final TextAttributesKey PY_PREDEFINED_USAGE = TextAttributesKey.createTextAttributesKey(
    "PY.PREDEFINED_USAGE", new TextAttributes(Color.magenta.darker(), null, null, null, Font.PLAIN)
  );

  public static final TextAttributesKey PY_BUILTIN_NAME = TextAttributesKey.createTextAttributesKey(
    "PY.BUILTIN_NAME", new TextAttributes(KEYWORD.getDefaultAttributes().getForegroundColor(), null, null, null, Font.PLAIN)
  );

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
