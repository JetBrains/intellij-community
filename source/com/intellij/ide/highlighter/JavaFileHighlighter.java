/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.ide.highlighter;

import com.intellij.lexer.JavaHighlightingLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.StringEscapesTokenTypes;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.java.IJavaDocElementType;
import com.intellij.psi.xml.XmlTokenType;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

public class JavaFileHighlighter extends SyntaxHighlighterBase {
  private static Map<IElementType, TextAttributesKey> ourMap1;
  private static Map<IElementType, TextAttributesKey> ourMap2;

  private LanguageLevel myLanguageLevel;

  public JavaFileHighlighter(LanguageLevel languageLevel) {
    myLanguageLevel = languageLevel;
  }

  static {
    ourMap1 = new HashMap<IElementType, TextAttributesKey>();
    ourMap2 = new HashMap<IElementType, TextAttributesKey>();

    fillMap(ourMap1, JavaTokenType.KEYWORD_BIT_SET, HighlighterColors.JAVA_KEYWORD);
    fillMap(ourMap1, JavaTokenType.OPERATION_BIT_SET, HighlighterColors.JAVA_OPERATION_SIGN);
    fillMap(ourMap1, JavaTokenType.OPERATION_BIT_SET, HighlighterColors.JAVA_OPERATION_SIGN);

    IElementType[] javadoc = IElementType.enumerate(new IElementType.Predicate() {
      public boolean matches(IElementType type) {
        return type instanceof IJavaDocElementType;
      }
    });

    for (int i = 0; i < javadoc.length; i++) {
      IElementType type = javadoc[i];
      ourMap1.put(type, HighlighterColors.JAVA_DOC_COMMENT);
    }

    ourMap1.put(XmlTokenType.XML_DATA_CHARACTERS, HighlighterColors.JAVA_DOC_COMMENT);
    ourMap1.put(XmlTokenType.XML_REAL_WHITE_SPACE, HighlighterColors.JAVA_DOC_COMMENT);
    ourMap1.put(XmlTokenType.TAG_WHITE_SPACE, HighlighterColors.JAVA_DOC_COMMENT);

    ourMap1.put(JavaTokenType.INTEGER_LITERAL, HighlighterColors.JAVA_NUMBER);
    ourMap1.put(JavaTokenType.LONG_LITERAL, HighlighterColors.JAVA_NUMBER);
    ourMap1.put(JavaTokenType.FLOAT_LITERAL, HighlighterColors.JAVA_NUMBER);
    ourMap1.put(JavaTokenType.DOUBLE_LITERAL, HighlighterColors.JAVA_NUMBER);
    ourMap1.put(JavaTokenType.STRING_LITERAL, HighlighterColors.JAVA_STRING);
    ourMap1.put(StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN, HighlighterColors.JAVA_VALID_STRING_ESCAPE);
    ourMap1.put(StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN, HighlighterColors.JAVA_INVALID_STRING_ESCAPE);
    ourMap1.put(StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN, HighlighterColors.JAVA_INVALID_STRING_ESCAPE);
    ourMap1.put(JavaTokenType.CHARACTER_LITERAL, HighlighterColors.JAVA_STRING);

    ourMap1.put(JavaTokenType.LPARENTH, HighlighterColors.JAVA_PARENTHS);
    ourMap1.put(JavaTokenType.RPARENTH, HighlighterColors.JAVA_PARENTHS);

    ourMap1.put(JavaTokenType.LBRACE, HighlighterColors.JAVA_BRACES);
    ourMap1.put(JavaTokenType.RBRACE, HighlighterColors.JAVA_BRACES);

    ourMap1.put(JavaTokenType.LBRACKET, HighlighterColors.JAVA_BRACKETS);
    ourMap1.put(JavaTokenType.RBRACKET, HighlighterColors.JAVA_BRACKETS);

    ourMap1.put(JavaTokenType.COMMA, HighlighterColors.JAVA_COMMA);
    ourMap1.put(JavaTokenType.DOT, HighlighterColors.JAVA_DOT);
    ourMap1.put(JavaTokenType.SEMICOLON, HighlighterColors.JAVA_SEMICOLON);

    //ourMap1[JavaTokenType.BOOLEAN_LITERAL] = HighlighterColors.JAVA_KEYWORD;
    //ourMap1[JavaTokenType.NULL_LITERAL] = HighlighterColors.JAVA_KEYWORD;
    ourMap1.put(JavaTokenType.C_STYLE_COMMENT, HighlighterColors.JAVA_BLOCK_COMMENT);
    ourMap1.put(JavaDocElementType.DOC_COMMENT, HighlighterColors.JAVA_DOC_COMMENT);
    ourMap1.put(JavaTokenType.END_OF_LINE_COMMENT, HighlighterColors.JAVA_LINE_COMMENT);
    ourMap1.put(JavaTokenType.BAD_CHARACTER, HighlighterColors.BAD_CHARACTER);

    ourMap1.put(JavaDocTokenType.DOC_TAG_NAME, HighlighterColors.JAVA_DOC_COMMENT);
    ourMap2.put(JavaDocTokenType.DOC_TAG_NAME, HighlighterColors.JAVA_DOC_TAG);

    IElementType[] javaDocMarkup = new IElementType[]{XmlTokenType.XML_START_TAG_START,
                                        XmlTokenType.XML_END_TAG_START,
                                        XmlTokenType.XML_TAG_END,
                                        XmlTokenType.XML_EMPTY_ELEMENT_END,
                                        XmlTokenType.TAG_WHITE_SPACE,
                                        XmlTokenType.XML_TAG_NAME,
                                        XmlTokenType.XML_NAME,
                                        XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN,
                                        XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER,
                                        XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER,
                                        XmlTokenType.XML_CHAR_ENTITY_REF,
                                        XmlTokenType.XML_EQ};

    for (int i = 0; i < javaDocMarkup.length; i++) {
      IElementType idx = javaDocMarkup[i];
      ourMap1.put(idx, HighlighterColors.JAVA_DOC_COMMENT);
      ourMap2.put(idx, HighlighterColors.JAVA_DOC_MARKUP);
    }
  }

  @NotNull
  public Lexer getHighlightingLexer() {
    return new JavaHighlightingLexer(myLanguageLevel);
  }

  @NotNull
  public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
    return pack(ourMap1.get(tokenType), ourMap2.get(tokenType));
  }
}