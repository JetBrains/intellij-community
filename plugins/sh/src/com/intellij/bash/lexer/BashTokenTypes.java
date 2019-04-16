package com.intellij.bash.lexer;

import com.intellij.bash.BashTypes;
import com.intellij.bash.psi.BashTokenType;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;


public interface BashTokenTypes extends BashTypes {
  IElementType BAD_CHARACTER = TokenType.BAD_CHARACTER;

  // common types
  IElementType WHITESPACE = TokenType.WHITE_SPACE;
  IElementType LINE_CONTINUATION = new BashTokenType("line continuation \\");

  TokenSet whitespaceTokens = TokenSet.create(WHITESPACE, LINE_CONTINUATION);

  // comments
  IElementType COMMENT = new BashTokenType("Comment");

  TokenSet commentTokens = TokenSet.create(COMMENT, SHEBANG);

  TokenSet HUMAN_READABLE_KEYWORDS_WITHOUT_TEMPLATES = TokenSet.create(
      DO, DONE, ELSE, ESAC, FI, IN, THEN, TIME
  );

  //conditional expressions
  TokenSet HUMAN_READABLE_KEYWORDS = TokenSet.create(
      CASE, DO, DONE,
      ELIF, ELSE, ESAC, FI, FOR, FUNCTION,
      IF, IN, SELECT, THEN, UNTIL, WHILE,
      TIME
  );

  TokenSet keywords = TokenSet.orSet(HUMAN_READABLE_KEYWORDS, TokenSet.create(
      BANG,
      LEFT_DOUBLE_BRACKET, RIGHT_DOUBLE_BRACKET,
      CASE_END,
      DOLLAR,
      LEFT_DOUBLE_PAREN, RIGHT_DOUBLE_PAREN, EXPR_CONDITIONAL_LEFT, EXPR_CONDITIONAL_RIGHT
  ));

  TokenSet internalCommands = TokenSet.create(TRAP, LET);

  //these are keyword tokens which may be used as identifiers, e.g. in a for loop
  //these tokens will be remapped to word tokens if they occur at a position where a word token would be accepted
  TokenSet identifierKeywords = TokenSet.create(
      CASE, DO, DONE, ELIF, ELSE, ESAC, FI, FOR, FUNCTION,
      IF, IN, SELECT, THEN, UNTIL, WHILE, TIME
  );

  TokenSet arithLiterals = TokenSet.create(NUMBER, OCTAL, HEX);

  TokenSet stringLiterals = TokenSet.create(WORD, RAW_STRING, INT, COLON);
  IElementType HEREDOC_LINE = new BashTokenType("heredoc line (temporary)");

  // test Operators
  TokenSet conditionalOperators = TokenSet.create(OR_OR, AND_AND, BANG, EQ, REGEXP, GT, LT);

  //this must NOT include PIPE_AMP because it's a command separator and not a real redirect token
  TokenSet redirectionSet = TokenSet.create(GT, LT, SHIFT_RIGHT,
      REDIRECT_HERE_STRING, REDIRECT_LESS_GREATER,
      REDIRECT_GREATER_BAR, REDIRECT_GREATER_AMP, REDIRECT_AMP_GREATER, REDIRECT_LESS_AMP, REDIRECT_AMP_GREATER_GREATER,
      HEREDOC_MARKER_TAG);
}
