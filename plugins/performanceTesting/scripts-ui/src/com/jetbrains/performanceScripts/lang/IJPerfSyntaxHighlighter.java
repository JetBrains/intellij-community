// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performanceScripts.lang;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.performanceScripts.lang.lexer.IJPerfLexerAdapter;
import com.jetbrains.performanceScripts.lang.psi.IJPerfElementTypes;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.editor.colors.TextAttributesKey.EMPTY_ARRAY;
import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

public class IJPerfSyntaxHighlighter extends SyntaxHighlighterBase {

  public static final TextAttributesKey COMMAND =
    createTextAttributesKey("PERF_PLUGIN_COMMAND", DefaultLanguageHighlighterColors.KEYWORD);
  public static final TextAttributesKey COMMENT =
    createTextAttributesKey("PERF_PLUGIN_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT);
  public static final TextAttributesKey NUMBER =
    createTextAttributesKey("PERF_PLUGIN_NUMBER", DefaultLanguageHighlighterColors.NUMBER);
  public static final TextAttributesKey TEXT =
    createTextAttributesKey("PERF_PLUGIN_TEXT", DefaultLanguageHighlighterColors.STRING);
  public static final TextAttributesKey OPTION =
    createTextAttributesKey("PERF_PLUGIN_OPTION", DefaultLanguageHighlighterColors.PARAMETER);
  public static final TextAttributesKey OPTION_SEPARATOR =
    createTextAttributesKey("PERF_PLUGIN_SEPARATOR", DefaultLanguageHighlighterColors.COMMA);
  public static final TextAttributesKey ASSIGNMENT =
    createTextAttributesKey("PERF_PLUGIN_ASSIGNMENT", DefaultLanguageHighlighterColors.OPERATION_SIGN);
  public static final TextAttributesKey FILE_PATH =
    createTextAttributesKey("PERF_PLUGIN_FILE_PATH", DefaultLanguageHighlighterColors.IDENTIFIER);

  @Override
  public @NotNull Lexer getHighlightingLexer() {
    return new IJPerfLexerAdapter();
  }

  @Override
  public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
    if (tokenType.equals(IJPerfElementTypes.COMMAND)) {
      return pack(COMMAND);
    }
    else if (tokenType.equals(IJPerfElementTypes.COMMENT)) {
      return pack(COMMENT);
    }
    else if (tokenType.equals(IJPerfElementTypes.NUMBER)) {
      return pack(NUMBER);
    }
    else if (tokenType.equals(IJPerfElementTypes.PIPE) ||
             tokenType.equals(IJPerfElementTypes.OPTIONS_SEPARATOR)) {
      return pack(OPTION_SEPARATOR);
    }
    else if (tokenType.equals(IJPerfElementTypes.TEXT)) {
      return pack(TEXT);
    }
    else if (tokenType.equals(IJPerfElementTypes.OPTION)) {
      return pack(OPTION);
    }
    else if (tokenType.equals(IJPerfElementTypes.ASSIGNMENT_OPERATOR)) {
      return pack(ASSIGNMENT);
    }
    else if (tokenType.equals(IJPerfElementTypes.FILE_PATH)) {
      return pack(FILE_PATH);
    }
    return EMPTY_ARRAY;
  }
}
