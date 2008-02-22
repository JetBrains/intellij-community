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
import com.intellij.openapi.editor.JavaHighlighterColors;
import com.intellij.openapi.editor.SyntaxHighlighterColors;
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
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class JavaFileHighlighter extends SyntaxHighlighterBase {
  private static final Map<IElementType, TextAttributesKey> ourMap1;
  private static final Map<IElementType, TextAttributesKey> ourMap2;

  private final LanguageLevel myLanguageLevel;

  public JavaFileHighlighter() {
    this(LanguageLevel.HIGHEST);
  }

  public JavaFileHighlighter(LanguageLevel languageLevel) {
    myLanguageLevel = languageLevel;
  }

  static {
    ourMap1 = new HashMap<IElementType, TextAttributesKey>();
    ourMap2 = new HashMap<IElementType, TextAttributesKey>();

    fillMap(ourMap1, JavaTokenType.KEYWORD_BIT_SET, SyntaxHighlighterColors.KEYWORD);
    fillMap(ourMap1, JavaTokenType.OPERATION_BIT_SET, SyntaxHighlighterColors.OPERATION_SIGN);
    fillMap(ourMap1, JavaTokenType.OPERATION_BIT_SET, SyntaxHighlighterColors.OPERATION_SIGN);

    IElementType[] javadoc = IElementType.enumerate(new IElementType.Predicate() {
      public boolean matches(IElementType type) {
        return type instanceof IJavaDocElementType;
      }
    });

    for (IElementType type : javadoc) {
      ourMap1.put(type, SyntaxHighlighterColors.DOC_COMMENT);
    }

    ourMap1.put(XmlTokenType.XML_DATA_CHARACTERS, SyntaxHighlighterColors.DOC_COMMENT);
    ourMap1.put(XmlTokenType.XML_REAL_WHITE_SPACE, SyntaxHighlighterColors.DOC_COMMENT);
    ourMap1.put(XmlTokenType.TAG_WHITE_SPACE, SyntaxHighlighterColors.DOC_COMMENT);

    ourMap1.put(JavaTokenType.INTEGER_LITERAL, SyntaxHighlighterColors.NUMBER);
    ourMap1.put(JavaTokenType.LONG_LITERAL, SyntaxHighlighterColors.NUMBER);
    ourMap1.put(JavaTokenType.FLOAT_LITERAL, SyntaxHighlighterColors.NUMBER);
    ourMap1.put(JavaTokenType.DOUBLE_LITERAL, SyntaxHighlighterColors.NUMBER);
    ourMap1.put(JavaTokenType.STRING_LITERAL, SyntaxHighlighterColors.STRING);
    ourMap1.put(StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN, JavaHighlighterColors.JAVA_VALID_STRING_ESCAPE);
    ourMap1.put(StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN, JavaHighlighterColors.JAVA_INVALID_STRING_ESCAPE);
    ourMap1.put(StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN, JavaHighlighterColors.JAVA_INVALID_STRING_ESCAPE);
    ourMap1.put(JavaTokenType.CHARACTER_LITERAL, SyntaxHighlighterColors.STRING);

    ourMap1.put(JavaTokenType.LPARENTH, SyntaxHighlighterColors.PARENTHS);
    ourMap1.put(JavaTokenType.RPARENTH, SyntaxHighlighterColors.PARENTHS);

    ourMap1.put(JavaTokenType.LBRACE, SyntaxHighlighterColors.BRACES);
    ourMap1.put(JavaTokenType.RBRACE, SyntaxHighlighterColors.BRACES);

    ourMap1.put(JavaTokenType.LBRACKET, SyntaxHighlighterColors.BRACKETS);
    ourMap1.put(JavaTokenType.RBRACKET, SyntaxHighlighterColors.BRACKETS);

    ourMap1.put(JavaTokenType.COMMA, SyntaxHighlighterColors.COMMA);
    ourMap1.put(JavaTokenType.DOT, SyntaxHighlighterColors.DOT);
    ourMap1.put(JavaTokenType.SEMICOLON, JavaHighlighterColors.JAVA_SEMICOLON);

    //ourMap1[JavaTokenType.BOOLEAN_LITERAL] = HighlighterColors.JAVA_KEYWORD;
    //ourMap1[JavaTokenType.NULL_LITERAL] = HighlighterColors.JAVA_KEYWORD;
    ourMap1.put(JavaTokenType.C_STYLE_COMMENT, SyntaxHighlighterColors.JAVA_BLOCK_COMMENT);
    ourMap1.put(JavaDocElementType.DOC_COMMENT, SyntaxHighlighterColors.DOC_COMMENT);
    ourMap1.put(JavaTokenType.END_OF_LINE_COMMENT, SyntaxHighlighterColors.LINE_COMMENT);
    ourMap1.put(JavaTokenType.BAD_CHARACTER, HighlighterColors.BAD_CHARACTER);

    ourMap1.put(JavaDocTokenType.DOC_TAG_NAME, SyntaxHighlighterColors.DOC_COMMENT);
    ourMap2.put(JavaDocTokenType.DOC_TAG_NAME, JavaHighlighterColors.JAVA_DOC_TAG);

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

    for (IElementType idx : javaDocMarkup) {
      ourMap1.put(idx, SyntaxHighlighterColors.DOC_COMMENT);
      ourMap2.put(idx, JavaHighlighterColors.JAVA_DOC_MARKUP);
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