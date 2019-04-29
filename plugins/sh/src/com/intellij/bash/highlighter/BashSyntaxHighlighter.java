package com.intellij.bash.highlighter;

import com.intellij.bash.lexer.BashLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

import static com.intellij.bash.lexer.BashTokenTypes.*;

/**
 * Defines bash token highlighting and formatting.
 */
public class BashSyntaxHighlighter extends SyntaxHighlighterBase {

  private static final TokenSet bracesSet = TokenSet.create(LEFT_CURLY, RIGHT_CURLY);
  private static final TokenSet numberSet = TokenSet.create(NUMBER, OCTAL, HEX, INT);
  private static final TokenSet bracketSet = TokenSet.create(LEFT_SQUARE, RIGHT_SQUARE);
  private static final TokenSet parenthesisSet = TokenSet.create(LEFT_PAREN, RIGHT_PAREN);
  private static final TokenSet conditionalOperators = TokenSet.create(OR_OR, AND_AND, BANG, EQ, REGEXP, GT, LT);
  private static final TokenSet redirectionSet = TokenSet.create(GT, LT, SHIFT_RIGHT, REDIRECT_HERE_STRING, REDIRECT_LESS_GREATER,
      REDIRECT_GREATER_BAR, REDIRECT_GREATER_AMP, REDIRECT_AMP_GREATER, REDIRECT_LESS_AMP, REDIRECT_AMP_GREATER_GREATER,
      HEREDOC_MARKER_TAG);

  private static final Map<IElementType, TextAttributesKey> map = new HashMap<>();

  static {
    map.put(VAR, BashHighlighterColors.VARIABLE);
    map.put(BACKQUOTE, BashHighlighterColors.BACKQUOTE);
    map.put(COMMENT, BashHighlighterColors.LINE_COMMENT);
    map.put(RAW_STRING, BashHighlighterColors.RAW_STRING);
    map.put(SHEBANG, BashHighlighterColors.SHEBANG_COMMENT);
    map.put(HEREDOC_CONTENT, BashHighlighterColors.HERE_DOC);
    map.put(HEREDOC_MARKER_END, BashHighlighterColors.HERE_DOC_END);
    map.put(HEREDOC_MARKER_START, BashHighlighterColors.HERE_DOC_START);
    map.put(LET, BashHighlighterColors.LET_COMMAND);
    map.put(BAD_CHARACTER, HighlighterColors.BAD_CHARACTER);
    fillMap(map, bracesSet, BashHighlighterColors.BRACE);
    fillMap(map, keywords, BashHighlighterColors.KEYWORD);
    fillMap(map, numberSet, BashHighlighterColors.NUMBER);
    fillMap(map, bracketSet, BashHighlighterColors.BRACKET);
    fillMap(map, parenthesisSet, BashHighlighterColors.PAREN);
    fillMap(map, redirectionSet, BashHighlighterColors.REDIRECTION);
    fillMap(map, conditionalOperators, BashHighlighterColors.CONDITIONAL_OPERATORS);
  }

  @NotNull
  public TextAttributesKey[] getTokenHighlights(final IElementType tokenType) {
    return pack(map.get(tokenType));
  }

  @NotNull
  public Lexer getHighlightingLexer() {
    return new BashLexer();
  }
}


