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

import static com.intellij.sh.lexer.ShTokenTypes.AND_AND;
import static com.intellij.sh.lexer.ShTokenTypes.BAD_CHARACTER;
import static com.intellij.sh.lexer.ShTokenTypes.BANG;
import static com.intellij.sh.lexer.ShTokenTypes.CLOSE_BACKQUOTE;
import static com.intellij.sh.lexer.ShTokenTypes.CLOSE_QUOTE;
import static com.intellij.sh.lexer.ShTokenTypes.COMMENT;
import static com.intellij.sh.lexer.ShTokenTypes.EQ;
import static com.intellij.sh.lexer.ShTokenTypes.EVAL;
import static com.intellij.sh.lexer.ShTokenTypes.GT;
import static com.intellij.sh.lexer.ShTokenTypes.HEREDOC_CONTENT;
import static com.intellij.sh.lexer.ShTokenTypes.HEREDOC_MARKER_END;
import static com.intellij.sh.lexer.ShTokenTypes.HEREDOC_MARKER_START;
import static com.intellij.sh.lexer.ShTokenTypes.HEREDOC_MARKER_TAG;
import static com.intellij.sh.lexer.ShTokenTypes.HEX;
import static com.intellij.sh.lexer.ShTokenTypes.INT;
import static com.intellij.sh.lexer.ShTokenTypes.LEFT_CURLY;
import static com.intellij.sh.lexer.ShTokenTypes.LEFT_PAREN;
import static com.intellij.sh.lexer.ShTokenTypes.LEFT_SQUARE;
import static com.intellij.sh.lexer.ShTokenTypes.LET;
import static com.intellij.sh.lexer.ShTokenTypes.LT;
import static com.intellij.sh.lexer.ShTokenTypes.NUMBER;
import static com.intellij.sh.lexer.ShTokenTypes.OCTAL;
import static com.intellij.sh.lexer.ShTokenTypes.OPEN_BACKQUOTE;
import static com.intellij.sh.lexer.ShTokenTypes.OPEN_QUOTE;
import static com.intellij.sh.lexer.ShTokenTypes.OR_OR;
import static com.intellij.sh.lexer.ShTokenTypes.RAW_STRING;
import static com.intellij.sh.lexer.ShTokenTypes.REDIRECT_AMP_GREATER;
import static com.intellij.sh.lexer.ShTokenTypes.REDIRECT_AMP_GREATER_GREATER;
import static com.intellij.sh.lexer.ShTokenTypes.REDIRECT_GREATER_AMP;
import static com.intellij.sh.lexer.ShTokenTypes.REDIRECT_GREATER_BAR;
import static com.intellij.sh.lexer.ShTokenTypes.REDIRECT_HERE_STRING;
import static com.intellij.sh.lexer.ShTokenTypes.REDIRECT_LESS_AMP;
import static com.intellij.sh.lexer.ShTokenTypes.REDIRECT_LESS_GREATER;
import static com.intellij.sh.lexer.ShTokenTypes.REGEXP;
import static com.intellij.sh.lexer.ShTokenTypes.RIGHT_CURLY;
import static com.intellij.sh.lexer.ShTokenTypes.RIGHT_PAREN;
import static com.intellij.sh.lexer.ShTokenTypes.RIGHT_SQUARE;
import static com.intellij.sh.lexer.ShTokenTypes.SHEBANG;
import static com.intellij.sh.lexer.ShTokenTypes.SHIFT_RIGHT;
import static com.intellij.sh.lexer.ShTokenTypes.STRING_CONTENT;
import static com.intellij.sh.lexer.ShTokenTypes.TEST;
import static com.intellij.sh.lexer.ShTokenTypes.VAR;
import static com.intellij.sh.lexer.ShTokenTypes.keywords;

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
  public @NotNull Lexer getHighlightingLexer() {
    return new ShLexer();
  }
}


