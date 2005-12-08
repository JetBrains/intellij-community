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
package com.intellij.ide.highlighter.custom;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.CustomHighlighterTokenType;
import com.intellij.psi.tree.IElementType;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

public class CustomFileHighlighter extends SyntaxHighlighterBase {
  private static Map<IElementType, TextAttributesKey> ourKeys;
  private SyntaxTable myTable;

  public CustomFileHighlighter(SyntaxTable table) {
    myTable = table;
  }

  static {
    ourKeys = new HashMap<IElementType, TextAttributesKey>();

    ourKeys.put(CustomHighlighterTokenType.KEYWORD_1, CustomHighlighterColors.CUSTOM_KEYWORD1_ATTRIBUTES);
    ourKeys.put(CustomHighlighterTokenType.KEYWORD_2, CustomHighlighterColors.CUSTOM_KEYWORD2_ATTRIBUTES);
    ourKeys.put(CustomHighlighterTokenType.KEYWORD_3, CustomHighlighterColors.CUSTOM_KEYWORD3_ATTRIBUTES);
    ourKeys.put(CustomHighlighterTokenType.KEYWORD_4, CustomHighlighterColors.CUSTOM_KEYWORD4_ATTRIBUTES);
    ourKeys.put(CustomHighlighterTokenType.NUMBER, CustomHighlighterColors.CUSTOM_NUMBER_ATTRIBUTES);
    ourKeys.put(CustomHighlighterTokenType.STRING, CustomHighlighterColors.CUSTOM_STRING_ATTRIBUTES);
    ourKeys.put(CustomHighlighterTokenType.LINE_COMMENT, CustomHighlighterColors.CUSTOM_LINE_COMMENT_ATTRIBUTES);
    ourKeys.put(CustomHighlighterTokenType.MULTI_LINE_COMMENT,
                CustomHighlighterColors.CUSTOM_MULTI_LINE_COMMENT_ATTRIBUTES);
  }

  @NotNull
  public Lexer getHighlightingLexer() {
    return new CustomFileTypeLexer(myTable);
  }

  @NotNull
  public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
    return pack(ourKeys.get(tokenType));
  }

  public static Map<IElementType, TextAttributesKey> getKeys() {
    return ourKeys;
  }
}