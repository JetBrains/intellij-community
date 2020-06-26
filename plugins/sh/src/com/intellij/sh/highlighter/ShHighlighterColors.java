// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.highlighter;

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;

public final class ShHighlighterColors {
  private ShHighlighterColors() {
  }

  public static final TextAttributesKey KEYWORD =
      TextAttributesKey.createTextAttributesKey("BASH.KEYWORD", DefaultLanguageHighlighterColors.KEYWORD);
  public static final TextAttributesKey LINE_COMMENT =
      TextAttributesKey.createTextAttributesKey("BASH.LINE_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT);
  public static final TextAttributesKey SHEBANG_COMMENT =
      TextAttributesKey.createTextAttributesKey("BASH.SHEBANG", LINE_COMMENT);

  public static final TextAttributesKey NUMBER =
      TextAttributesKey.createTextAttributesKey("BASH.NUMBER", DefaultLanguageHighlighterColors.NUMBER);
  public static final TextAttributesKey STRING =
      TextAttributesKey.createTextAttributesKey("BASH.STRING", DefaultLanguageHighlighterColors.STRING);
  public static final TextAttributesKey RAW_STRING =
      TextAttributesKey.createTextAttributesKey("BASH.RAW_STRING", DefaultLanguageHighlighterColors.STRING);
  public static final TextAttributesKey VARIABLE =
      TextAttributesKey.createTextAttributesKey("BASH.VAR_USE", DefaultLanguageHighlighterColors.GLOBAL_VARIABLE);
  public static final TextAttributesKey VARIABLE_DECLARATION =
      TextAttributesKey.createTextAttributesKey("BASH.VAR_DEF", VARIABLE);
  public static final TextAttributesKey COMPOSED_VARIABLE =
      TextAttributesKey.createTextAttributesKey("BASH.VAR_USE_COMPOSED", VARIABLE);

  public static final TextAttributesKey HERE_DOC =
      TextAttributesKey.createTextAttributesKey("BASH.HERE_DOC", DefaultLanguageHighlighterColors.TEMPLATE_LANGUAGE_COLOR);
  public static final TextAttributesKey HERE_DOC_START =
      TextAttributesKey.createTextAttributesKey("BASH.HERE_DOC_START", DefaultLanguageHighlighterColors.KEYWORD);
  public static final TextAttributesKey HERE_DOC_END =
      TextAttributesKey.createTextAttributesKey("BASH.HERE_DOC_END", DefaultLanguageHighlighterColors.KEYWORD);

  public static final TextAttributesKey PAREN =
      TextAttributesKey.createTextAttributesKey("BASH.PAREN", DefaultLanguageHighlighterColors.PARENTHESES);
  public static final TextAttributesKey BRACE =
      TextAttributesKey.createTextAttributesKey("BASH.BRACE", DefaultLanguageHighlighterColors.BRACES);
  public static final TextAttributesKey BRACKET =
      TextAttributesKey.createTextAttributesKey("BASH.BRACKET", DefaultLanguageHighlighterColors.BRACKETS);

  public static final TextAttributesKey REDIRECTION =
      TextAttributesKey.createTextAttributesKey("BASH.REDIRECTION", DefaultLanguageHighlighterColors.OPERATION_SIGN);
  public static final TextAttributesKey CONDITIONAL_OPERATORS =
      TextAttributesKey.createTextAttributesKey("BASH.CONDITIONAL", DefaultLanguageHighlighterColors.KEYWORD);

  public static final TextAttributesKey GENERIC_COMMAND =
      TextAttributesKey.createTextAttributesKey("BASH.EXTERNAL_COMMAND", DefaultLanguageHighlighterColors.IDENTIFIER);
  public static final TextAttributesKey SUBSHELL_COMMAND =
      TextAttributesKey.createTextAttributesKey("BASH.SUBSHELL_COMMAND", GENERIC_COMMAND);
  public static final TextAttributesKey BACKQUOTE =
      TextAttributesKey.createTextAttributesKey("BASH.BACKQUOTE", GENERIC_COMMAND);
  public static final TextAttributesKey FUNCTION_DECLARATION =
      TextAttributesKey.createTextAttributesKey("BASH.FUNCTION_DEF_NAME", DefaultLanguageHighlighterColors.FUNCTION_DECLARATION);

/*  public static final TextAttributesKey BINARY_DATA = TODO:: Add support for this color settings
      TextAttributesKey.createTextAttributesKey("BASH.BINARY_DATA");
  public static final TextAttributesKey INTERNAL_BUILTINS_COMMAND =
      TextAttributesKey.createTextAttributesKey("BASH.INTERNAL_COMMAND", GENERIC_COMMAND);  TODO:: Think about the support of this setting
  public static final TextAttributesKey FUNCTION_CALL =
      TextAttributesKey.createTextAttributesKey("BASH.FUNCTION_CALL", DefaultLanguageHighlighterColors.FUNCTION_CALL);
  public static final TextAttributesKey VAR_USE_BUILTIN =
      TextAttributesKey.createTextAttributesKey("BASH.VAR_USE_BUILTIN", VAR_USE);*/


}
