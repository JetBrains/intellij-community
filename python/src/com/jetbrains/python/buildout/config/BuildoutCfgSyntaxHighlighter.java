// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.jetbrains.python.buildout.config;

import com.google.common.collect.Maps;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.buildout.config.lexer.BuildoutCfgFlexLexer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author: traff
 */
public class BuildoutCfgSyntaxHighlighter extends SyntaxHighlighterBase implements BuildoutCfgTokenTypes {
  @NonNls
  static final String COMMENT_ID = "BUILDOUT_COMMENT";
  @NonNls
  static final String TEXT_ID = "BUILDOUT_TEXT";
  @NonNls
  static final String BRACKETS_ID = "BUILDOUT_BRACKETS";


  public static final TextAttributesKey BUILDOUT_SECTION_NAME = TextAttributesKey.createTextAttributesKey(
    "BUILDOUT.SECTION_NAME",
    DefaultLanguageHighlighterColors.NUMBER
  );

  public static final TextAttributesKey BUILDOUT_KEY = TextAttributesKey.createTextAttributesKey(
    "BUILDOUT.KEY",
    DefaultLanguageHighlighterColors.KEYWORD
  );

  public static final TextAttributesKey BUILDOUT_VALUE = TextAttributesKey.createTextAttributesKey(
    "BUILDOUT.VALUE",
    DefaultLanguageHighlighterColors.STRING
  );

  public static final TextAttributesKey BUILDOUT_COMMENT = TextAttributesKey.createTextAttributesKey(
    "BUILDOUT.LINE_COMMENT",
    DefaultLanguageHighlighterColors.LINE_COMMENT
  );

  public static final TextAttributesKey BUILDOUT_KEY_VALUE_SEPARATOR = TextAttributesKey.createTextAttributesKey(
    "BUILDOUT.KEY_VALUE_SEPARATOR",
    DefaultLanguageHighlighterColors.OPERATION_SIGN
  );
  public static final TextAttributesKey BUILDOUT_VALID_STRING_ESCAPE = TextAttributesKey.createTextAttributesKey(
    "BUILDOUT.VALID_STRING_ESCAPE",
    DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE
  );
  public static final TextAttributesKey BUILDOUT_INVALID_STRING_ESCAPE = TextAttributesKey.createTextAttributesKey(
    "BUILDOUT.INVALID_STRING_ESCAPE",
    DefaultLanguageHighlighterColors.INVALID_STRING_ESCAPE
  );


  private static final Map<IElementType, TextAttributesKey> ATTRIBUTES = Maps.newHashMap();

  static {
    ATTRIBUTES.put(COMMENT, BUILDOUT_COMMENT);
    ATTRIBUTES.put(SECTION_NAME, BUILDOUT_SECTION_NAME);
    ATTRIBUTES.put(KEY_VALUE_SEPARATOR, BUILDOUT_KEY_VALUE_SEPARATOR);
    ATTRIBUTES.put(KEY_CHARACTERS, BUILDOUT_KEY);
    ATTRIBUTES.put(VALUE_CHARACTERS, BUILDOUT_VALUE);
  }


  @Override
  @NotNull
  public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
    return SyntaxHighlighterBase.pack(ATTRIBUTES.get(tokenType));
  }

  @Override
  @NotNull
  public Lexer getHighlightingLexer() {
    return new BuildoutCfgFlexLexer();
  }
}
