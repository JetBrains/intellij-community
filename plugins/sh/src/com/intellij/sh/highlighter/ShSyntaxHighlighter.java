// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.highlighter;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.sh.lexer.ShLexer;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

import static com.intellij.sh.lexer.ShTokenTypes.*;

/**
 * Defines sh token highlighting and formatting.
 */
public class ShSyntaxHighlighter extends SyntaxHighlighterBase {

  private static final TokenSet stringSet = TokenSet.create(OPEN_QUOTE, STRING_CONTENT, CLOSE_QUOTE);
  private static final TokenSet bracesSet = TokenSet.create(LEFT_CURLY, RIGHT_CURLY);
  private static final TokenSet backQuoteSet = TokenSet.create(OPEN_BACKQUOTE, CLOSE_BACKQUOTE);
  private static final TokenSet numberSet = TokenSet.create(NUMBER, OCTAL, HEX, INT);
  private static final TokenSet bracketSet = TokenSet.create(LEFT_SQUARE, RIGHT_SQUARE);
  private static final TokenSet parenthesisSet = TokenSet.create(LEFT_PAREN, RIGHT_PAREN);
  private static final TokenSet commandSet = TokenSet.create(LET, EVAL, TEST);
  private static final TokenSet conditionalOperators = TokenSet.create(OR_OR, AND_AND, BANG, EQ, REGEXP, GT, LT);
  private static final TokenSet redirectionSet = TokenSet.create(GT, LT, SHIFT_RIGHT, REDIRECT_HERE_STRING, REDIRECT_LESS_GREATER,
      REDIRECT_GREATER_BAR, REDIRECT_GREATER_AMP, REDIRECT_AMP_GREATER, REDIRECT_LESS_AMP, REDIRECT_AMP_GREATER_GREATER,
      HEREDOC_MARKER_TAG);

  private static final Map<IElementType, TextAttributesKey> map = new HashMap<>();

  static {
    map.put(VAR, ShHighlighterColors.VARIABLE);
    map.put(COMMENT, ShHighlighterColors.LINE_COMMENT);
    map.put(RAW_STRING, ShHighlighterColors.RAW_STRING);
    map.put(SHEBANG, ShHighlighterColors.SHEBANG_COMMENT);
    map.put(HEREDOC_CONTENT, ShHighlighterColors.HERE_DOC);
    map.put(HEREDOC_MARKER_END, ShHighlighterColors.HERE_DOC_END);
    map.put(HEREDOC_MARKER_START, ShHighlighterColors.HERE_DOC_START);
    map.put(BAD_CHARACTER, HighlighterColors.BAD_CHARACTER);
    fillMap(map, commandSet, ShHighlighterColors.GENERIC_COMMAND);
    fillMap(map, stringSet, ShHighlighterColors.STRING);
    fillMap(map, bracesSet, ShHighlighterColors.BRACE);
    fillMap(map, backQuoteSet, ShHighlighterColors.BACKQUOTE);
    fillMap(map, keywords, ShHighlighterColors.KEYWORD);
    fillMap(map, numberSet, ShHighlighterColors.NUMBER);
    fillMap(map, bracketSet, ShHighlighterColors.BRACKET);
    fillMap(map, parenthesisSet, ShHighlighterColors.PAREN);
    fillMap(map, redirectionSet, ShHighlighterColors.REDIRECTION);
    fillMap(map, conditionalOperators, ShHighlighterColors.CONDITIONAL_OPERATORS);
  }

  @Override
  public TextAttributesKey @NotNull [] getTokenHighlights(final IElementType tokenType) {
    return pack(map.get(tokenType));
  }

  @Override
  @NotNull
  public Lexer getHighlightingLexer() {
    return new ShLexer();
  }
}


