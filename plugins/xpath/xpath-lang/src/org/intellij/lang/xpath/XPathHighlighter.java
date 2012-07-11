/*
 * Copyright 2005 Sascha Weinreuter
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
package org.intellij.lang.xpath;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.SyntaxHighlighterColors;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.colors.TextAttributesKeyDefaults;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings({"RawUseOfParameterizedType", "unchecked"})
public class XPathHighlighter extends SyntaxHighlighterBase {
    private static final Map keys1;
    private static final Map keys1_2;
    private static final Map keys2;

  private final boolean myXPath2Syntax;

  public XPathHighlighter(boolean xpath2Syntax) {
    myXPath2Syntax = xpath2Syntax;
  }

  @NotNull
    public Lexer getHighlightingLexer() {
        return XPathLexer.create(myXPath2Syntax);
    }

    static final TextAttributesKey XPATH_KEYWORD = TextAttributesKeyDefaults.createTextAttributesKey(
      "XPATH.KEYWORD",
      TextAttributesKeyDefaults.getDefaultAttributes(SyntaxHighlighterColors.KEYWORD)
    );

    static final TextAttributesKey XPATH_STRING = TextAttributesKeyDefaults.createTextAttributesKey(
      "XPATH.STRING",
      TextAttributesKeyDefaults.getDefaultAttributes(SyntaxHighlighterColors.STRING)
    );

    static final TextAttributesKey XPATH_NUMBER = TextAttributesKeyDefaults.createTextAttributesKey(
      "XPATH.NUMBER",
      TextAttributesKeyDefaults.getDefaultAttributes(SyntaxHighlighterColors.NUMBER)
    );

    static final TextAttributesKey XPATH_OPERATION_SIGN = TextAttributesKeyDefaults.createTextAttributesKey(
      "XPATH.OPERATION_SIGN",
      TextAttributesKeyDefaults.getDefaultAttributes(SyntaxHighlighterColors.OPERATION_SIGN)
    );

    static final TextAttributesKey XPATH_PARENTH = TextAttributesKeyDefaults.createTextAttributesKey(
      "XPATH.PARENTH",
      TextAttributesKeyDefaults.getDefaultAttributes(SyntaxHighlighterColors.PARENTHS)
    );

    static final TextAttributesKey XPATH_BRACKET = TextAttributesKeyDefaults.createTextAttributesKey(
      "XPATH.BRACKET",
      TextAttributesKeyDefaults.getDefaultAttributes(SyntaxHighlighterColors.BRACKETS)
    );

    static final TextAttributesKey XPATH_FUNCTION = TextAttributesKeyDefaults.createTextAttributesKey(
      "XPATH.FUNCTION",
      TextAttributesKeyDefaults.getDefaultAttributes(CodeInsightColors.STATIC_METHOD_ATTRIBUTES)
    );

    static final TextAttributesKey XPATH_VARIABLE = TextAttributesKeyDefaults.createTextAttributesKey(
      "XPATH.XPATH_VARIABLE",
      TextAttributesKeyDefaults.getDefaultAttributes(CodeInsightColors.INSTANCE_FIELD_ATTRIBUTES)
    );

    static final TextAttributesKey XPATH_PREFIX = TextAttributesKeyDefaults.createTextAttributesKey(
      "XPATH.XPATH_PREFIX",
      TextAttributesKeyDefaults.getDefaultAttributes(HighlighterColors.TEXT)
    );

    static final TextAttributesKey XPATH_NAME = TextAttributesKeyDefaults.createTextAttributesKey(
      "XPATH.XPATH_NAME",
      TextAttributesKeyDefaults.getDefaultAttributes(HighlighterColors.TEXT)
    );

    static final TextAttributesKey XPATH_TEXT = TextAttributesKeyDefaults.createTextAttributesKey(
      "XPATH.XPATH_TEXT",
      TextAttributesKeyDefaults.getDefaultAttributes(HighlighterColors.TEXT)
    );

    static {
        keys1 = new HashMap();
        keys2 = new HashMap();

        fillMap(keys1, XPathTokenTypes.BINARY_OPERATIONS, XPATH_OPERATION_SIGN);
        fillMap(keys1, XPathTokenTypes.KEYWORDS, XPATH_KEYWORD);

        fillMap(keys1, XPathTokenTypes.REST, XPATH_TEXT);
        
        keys1.put(XPathTokenTypes.NCNAME, XPATH_NAME);

        keys1.put(XPathTokenTypes.NUMBER, XPATH_NUMBER);
        keys1.put(XPathTokenTypes.STRING_LITERAL, XPATH_STRING);
        keys1.put(XPathTokenTypes.FUNCTION_NAME, XPATH_FUNCTION);
        keys1.put(XPathTokenTypes.EXT_PREFIX, XPATH_PREFIX);

        keys1.put(XPathTokenTypes.DOLLAR, XPATH_VARIABLE);
        keys1.put(XPathTokenTypes.VARIABLE_NAME, XPATH_VARIABLE);
        keys1.put(XPathTokenTypes.VARIABLE_PREFIX, XPATH_PREFIX);

        keys1.put(XPathTokenTypes.LPAREN, XPATH_PARENTH);
        keys1.put(XPathTokenTypes.LBRACKET, XPATH_BRACKET);
        keys1.put(XPathTokenTypes.RPAREN, XPATH_PARENTH);
        keys1.put(XPathTokenTypes.RBRACKET, XPATH_BRACKET);

        keys1.put(XPathTokenTypes.BAD_CHARACTER, HighlighterColors.BAD_CHARACTER);
        keys1.put(XPathTokenTypes.BAD_AXIS_NAME, CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES);

      keys1_2 = new HashMap(keys1);
      fillMap(keys1_2, XPath2TokenTypes.KEYWORDS, XPATH_KEYWORD);
    }

    @NotNull
    public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
        return pack(((TextAttributesKey)(myXPath2Syntax ? keys1_2 : keys1).get(tokenType)), ((TextAttributesKey)keys2.get(tokenType)));
    }
}
