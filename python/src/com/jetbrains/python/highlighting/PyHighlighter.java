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
package com.jetbrains.python.highlighting;

import com.intellij.lexer.LayeredLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.StringEscapesTokenTypes;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.lexer.PyStringLiteralLexer;
import com.jetbrains.python.lexer.PythonHighlightingLexer;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

import static com.intellij.openapi.editor.DefaultLanguageHighlighterColors.*;

/**
 * Colors and lexer(s) needed for highlighting.
 */
public class PyHighlighter extends SyntaxHighlighterBase {
  private Map<IElementType, TextAttributesKey> keys;
  private final LanguageLevel myLanguageLevel;

  @NotNull
  public Lexer getHighlightingLexer() {
    LayeredLexer ret = new LayeredLexer(createHighlightingLexer(myLanguageLevel));
    ret.registerLayer(
      new PyStringLiteralLexer(PyTokenTypes.SINGLE_QUOTED_STRING),
      PyTokenTypes.SINGLE_QUOTED_STRING
    );
    ret.registerLayer(
      new PyStringLiteralLexer(PyTokenTypes.SINGLE_QUOTED_UNICODE),
      PyTokenTypes.SINGLE_QUOTED_UNICODE
    );
    ret.registerLayer(
      new PyStringLiteralLexer(PyTokenTypes.TRIPLE_QUOTED_STRING),
      PyTokenTypes.TRIPLE_QUOTED_STRING
    );
    ret.registerLayer(
      new PyStringLiteralLexer(PyTokenTypes.TRIPLE_QUOTED_UNICODE),
      PyTokenTypes.TRIPLE_QUOTED_UNICODE
    );

    return ret;
  }

  protected PythonHighlightingLexer createHighlightingLexer(LanguageLevel languageLevel) {
    return new PythonHighlightingLexer(myLanguageLevel);
  }

  public static final TextAttributesKey PY_KEYWORD = TextAttributesKey.createTextAttributesKey("PY.KEYWORD", KEYWORD);

  public static final TextAttributesKey PY_BYTE_STRING = TextAttributesKey.createTextAttributesKey("PY.STRING.B", STRING);
  public static final TextAttributesKey PY_UNICODE_STRING = TextAttributesKey.createTextAttributesKey("PY.STRING.U", STRING);
  public static final TextAttributesKey PY_NUMBER = TextAttributesKey.createTextAttributesKey("PY.NUMBER", NUMBER);

  static final TextAttributesKey PY_OPERATION_SIGN = TextAttributesKey.createTextAttributesKey("PY.OPERATION_SIGN", OPERATION_SIGN);

  static final TextAttributesKey PY_PARENTHS = TextAttributesKey.createTextAttributesKey("PY.PARENTHS", PARENTHESES);

  static final TextAttributesKey PY_BRACKETS = TextAttributesKey.createTextAttributesKey("PY.BRACKETS", BRACKETS);

  static final TextAttributesKey PY_BRACES = TextAttributesKey.createTextAttributesKey("PY.BRACES", BRACES);

  static final TextAttributesKey PY_COMMA = TextAttributesKey.createTextAttributesKey("PY.COMMA", COMMA);

  static final TextAttributesKey PY_DOT = TextAttributesKey.createTextAttributesKey("PY.DOT", DOT);

  public static final TextAttributesKey PY_LINE_COMMENT = TextAttributesKey.createTextAttributesKey("PY.LINE_COMMENT", LINE_COMMENT);

  public static final TextAttributesKey PY_DOC_COMMENT = TextAttributesKey.createTextAttributesKey("PY.DOC_COMMENT", DOC_COMMENT);

  public static final TextAttributesKey PY_DOC_COMMENT_TAG = TextAttributesKey.createTextAttributesKey("PY.DOC_COMMENT_TAG", DOC_COMMENT_TAG);

  public static final TextAttributesKey PY_DECORATOR = TextAttributesKey.createTextAttributesKey("PY.DECORATOR", IDENTIFIER);

  public static final TextAttributesKey PY_CLASS_DEFINITION = TextAttributesKey.createTextAttributesKey("PY.CLASS_DEFINITION", CLASS_NAME);

  public static final TextAttributesKey PY_FUNC_DEFINITION = TextAttributesKey.createTextAttributesKey("PY.FUNC_DEFINITION", FUNCTION_DECLARATION);

  public static final TextAttributesKey PY_PREDEFINED_DEFINITION = TextAttributesKey.createTextAttributesKey("PY.PREDEFINED_DEFINITION", PREDEFINED_SYMBOL);

  public static final TextAttributesKey PY_PREDEFINED_USAGE = TextAttributesKey.createTextAttributesKey("PY.PREDEFINED_USAGE", PREDEFINED_SYMBOL);

  public static final TextAttributesKey PY_BUILTIN_NAME = TextAttributesKey.createTextAttributesKey("PY.BUILTIN_NAME", PREDEFINED_SYMBOL);

  public static final TextAttributesKey PY_PARAMETER = TextAttributesKey.createTextAttributesKey("PY.PARAMETER", PARAMETER);
  public static final TextAttributesKey PY_SELF_PARAMETER = TextAttributesKey.createTextAttributesKey( "PY.SELF_PARAMETER", PARAMETER);

  public static final TextAttributesKey PY_KEYWORD_ARGUMENT = TextAttributesKey.createTextAttributesKey("PY.KEYWORD_ARGUMENT", PARAMETER);

  public static final TextAttributesKey PY_FUNCTION_CALL = TextAttributesKey.createTextAttributesKey("PY.FUNCTION_CALL", FUNCTION_CALL);

  public static final TextAttributesKey PY_VALID_STRING_ESCAPE = TextAttributesKey.createTextAttributesKey("PY.VALID_STRING_ESCAPE", VALID_STRING_ESCAPE);

  public static final TextAttributesKey PY_INVALID_STRING_ESCAPE = TextAttributesKey.createTextAttributesKey("PY.INVALID_STRING_ESCAPE", INVALID_STRING_ESCAPE);
  
  /**
   * The 'heavy' constructor that initializes everything. PySyntaxHighlighterFactory caches such instances per level.
   * @param languageLevel
   */
  public PyHighlighter(LanguageLevel languageLevel) {
    myLanguageLevel = languageLevel;
    keys = new HashMap<>();

    fillMap(keys, PythonDialectsTokenSetProvider.INSTANCE.getKeywordTokens(), PY_KEYWORD);
    fillMap(keys, PyTokenTypes.OPERATIONS, PY_OPERATION_SIGN);

    keys.put(PyTokenTypes.INTEGER_LITERAL, PY_NUMBER);
    keys.put(PyTokenTypes.FLOAT_LITERAL, PY_NUMBER);
    keys.put(PyTokenTypes.IMAGINARY_LITERAL, PY_NUMBER);
    keys.put(PyTokenTypes.SINGLE_QUOTED_STRING, PY_BYTE_STRING);
    keys.put(PyTokenTypes.TRIPLE_QUOTED_STRING, PY_BYTE_STRING);
    keys.put(PyTokenTypes.SINGLE_QUOTED_UNICODE, PY_UNICODE_STRING);
    keys.put(PyTokenTypes.TRIPLE_QUOTED_UNICODE, PY_UNICODE_STRING);

    keys.put(PyTokenTypes.DOCSTRING, PY_DOC_COMMENT);

    keys.put(PyTokenTypes.LPAR, PY_PARENTHS);
    keys.put(PyTokenTypes.RPAR, PY_PARENTHS);

    keys.put(PyTokenTypes.LBRACE, PY_BRACES);
    keys.put(PyTokenTypes.RBRACE, PY_BRACES);

    keys.put(PyTokenTypes.LBRACKET, PY_BRACKETS);
    keys.put(PyTokenTypes.RBRACKET, PY_BRACKETS);

    keys.put(PyTokenTypes.COMMA, PY_COMMA);
    keys.put(PyTokenTypes.DOT, PY_DOT);

    keys.put(PyTokenTypes.END_OF_LINE_COMMENT, PY_LINE_COMMENT);
    keys.put(PyTokenTypes.BAD_CHARACTER, HighlighterColors.BAD_CHARACTER);

    keys.put(StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN, PY_VALID_STRING_ESCAPE);
    keys.put(StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN, PY_INVALID_STRING_ESCAPE);
    keys.put(StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN, PY_INVALID_STRING_ESCAPE);
  }

  @NotNull
  public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
    return pack(keys.get(tokenType));
  }
}
