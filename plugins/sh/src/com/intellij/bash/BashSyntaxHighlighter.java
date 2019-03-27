package com.intellij.bash;

import com.intellij.bash.lexer.BashLexer;
import com.intellij.bash.lexer.BashTokenTypes;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

import static com.intellij.bash.lexer.BashTokenTypes.conditionalOperators;
import static com.intellij.bash.lexer.BashTokenTypes.redirectionSet;

/**
 * Defines bash token highlighting and formatting.
 */
public class BashSyntaxHighlighter extends SyntaxHighlighterBase {
  //not type highlighting
  public static final TextAttributesKey KEYWORD = TextAttributesKey.createTextAttributesKey("BASH.KEYWORD", DefaultLanguageHighlighterColors.KEYWORD);

  public static final TextAttributesKey LINE_COMMENT = TextAttributesKey.createTextAttributesKey("BASH.LINE_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT);

  public static final TextAttributesKey SHEBANG_COMMENT = TextAttributesKey.createTextAttributesKey("BASH.SHEBANG", LINE_COMMENT);

  public static final TextAttributesKey PAREN = TextAttributesKey.createTextAttributesKey("BASH.PAREN", DefaultLanguageHighlighterColors.PARENTHESES);
  public static final TextAttributesKey BRACE = TextAttributesKey.createTextAttributesKey("BASH.BRACE", DefaultLanguageHighlighterColors.BRACES);
  public static final TextAttributesKey BRACKET = TextAttributesKey.createTextAttributesKey("BASH.BRACKET", DefaultLanguageHighlighterColors.BRACKETS);

  public static final TextAttributesKey NUMBER = TextAttributesKey.createTextAttributesKey("BASH.NUMBER", DefaultLanguageHighlighterColors.NUMBER);
  public static final TextAttributesKey REDIRECTION = TextAttributesKey.createTextAttributesKey("BASH.REDIRECTION", DefaultLanguageHighlighterColors.OPERATION_SIGN);
  public static final TextAttributesKey CONDITIONAL = TextAttributesKey.createTextAttributesKey("BASH.CONDITIONAL", DefaultLanguageHighlighterColors.KEYWORD);

  public static final TextAttributesKey RAW_STRING = TextAttributesKey.createTextAttributesKey("BASH.RAW_STRING", DefaultLanguageHighlighterColors.STRING);

  //psi highlighting
  public static final TextAttributesKey BINARY_DATA = TextAttributesKey.createTextAttributesKey("BASH.BINARY_DATA");

  public static final TextAttributesKey STRING = TextAttributesKey.createTextAttributesKey("BASH.STRING", DefaultLanguageHighlighterColors.STRING);

  public static final TextAttributesKey HERE_DOC = TextAttributesKey.createTextAttributesKey("BASH.HERE_DOC", DefaultLanguageHighlighterColors.TEMPLATE_LANGUAGE_COLOR);
  public static final TextAttributesKey HERE_DOC_START = TextAttributesKey.createTextAttributesKey("BASH.HERE_DOC_START", DefaultLanguageHighlighterColors.KEYWORD);
  public static final TextAttributesKey HERE_DOC_END = TextAttributesKey.createTextAttributesKey("BASH.HERE_DOC_END", DefaultLanguageHighlighterColors.KEYWORD);

  public static final TextAttributesKey EXTERNAL_COMMAND = TextAttributesKey.createTextAttributesKey("BASH.EXTERNAL_COMMAND", DefaultLanguageHighlighterColors.IDENTIFIER);
  public static final TextAttributesKey INTERNAL_COMMAND = TextAttributesKey.createTextAttributesKey("BASH.INTERNAL_COMMAND", EXTERNAL_COMMAND);
  public static final TextAttributesKey SUBSHELL_COMMAND = TextAttributesKey.createTextAttributesKey("BASH.SUBSHELL_COMMAND", EXTERNAL_COMMAND);
  //also used in the lexer highlighting
  public static final TextAttributesKey BACKQUOTE = TextAttributesKey.createTextAttributesKey("BASH.BACKQUOTE", SUBSHELL_COMMAND);

  public static final TextAttributesKey FUNCTION_DEF_NAME = TextAttributesKey.createTextAttributesKey("BASH.FUNCTION_DEF_NAME", DefaultLanguageHighlighterColors.FUNCTION_DECLARATION);
  public static final TextAttributesKey FUNCTION_CALL = TextAttributesKey.createTextAttributesKey("BASH.FUNCTION_CALL", DefaultLanguageHighlighterColors.FUNCTION_CALL);

  public static final TextAttributesKey VAR_USE = TextAttributesKey.createTextAttributesKey("BASH.VAR_USE", DefaultLanguageHighlighterColors.GLOBAL_VARIABLE);
  public static final TextAttributesKey VAR_USE_BUILTIN = TextAttributesKey.createTextAttributesKey("BASH.VAR_USE_BUILTIN", VAR_USE);
  public static final TextAttributesKey VAR_USE_COMPOSED = TextAttributesKey.createTextAttributesKey("BASH.VAR_USE_COMPOSED", VAR_USE);

  public static final TextAttributesKey VAR_DEF = TextAttributesKey.createTextAttributesKey("BASH.VAR_DEF", VAR_USE);

  @NotNull
  public Lexer getHighlightingLexer() {
    return new BashLexer();
  }

  private static final Map<IElementType, TextAttributesKey> attributes1 = new HashMap<>();

  private static final TokenSet parenthesisSet = TokenSet.create(BashTokenTypes.LEFT_PAREN, BashTokenTypes.RIGHT_PAREN);
  private static final TokenSet bracesSet = TokenSet.create(BashTokenTypes.LEFT_CURLY, BashTokenTypes.RIGHT_CURLY);
  private static final TokenSet bracketSet = TokenSet.create(BashTokenTypes.LEFT_SQUARE, BashTokenTypes.RIGHT_SQUARE);
  private static final TokenSet numberSet = TokenSet.orSet(BashTokenTypes.arithLiterals, TokenSet.create(BashTokenTypes.INT));
  private static final TokenSet lineCommentSet = TokenSet.create(BashTokenTypes.COMMENT);
  private static final TokenSet shebangSet = TokenSet.create(BashTokenTypes.SHEBANG);
  private static final TokenSet backquoteSet = TokenSet.create(BashTokenTypes.BACKQUOTE);

  private static final TokenSet badCharacterSet = TokenSet.create(BashTokenTypes.BAD_CHARACTER);

  static {
    fillMap(attributes1, BashTokenTypes.keywords, KEYWORD);
    fillMap(attributes1, BashTokenTypes.internalCommands, INTERNAL_COMMAND);

//    fillMap(attributes1, BINARY_DATA, BashTokenTypes.BINARY_DATA); // todo

    fillMap(attributes1, backquoteSet, BACKQUOTE);

    fillMap(attributes1, lineCommentSet, LINE_COMMENT);

    fillMap(attributes1, shebangSet, SHEBANG_COMMENT);

    fillMap(attributes1, RAW_STRING, BashTokenTypes.RAW_STRING);

    fillMap(attributes1, VAR_USE, BashTokenTypes.VAR);

    fillMap(attributes1, parenthesisSet, PAREN);
    fillMap(attributes1, bracesSet, BRACE);
    fillMap(attributes1, bracketSet, BRACKET);
    fillMap(attributes1, numberSet, NUMBER);

    fillMap(attributes1, redirectionSet, REDIRECTION);
    fillMap(attributes1, conditionalOperators, CONDITIONAL);

    fillMap(attributes1, badCharacterSet, HighlighterColors.BAD_CHARACTER);
  }

  @NotNull
  public TextAttributesKey[] getTokenHighlights(final IElementType tokenType) {
    return pack(attributes1.get(tokenType));
  }
}


