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

  IElementType IN = new BashTokenType("in");

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

  //arithmetic operators: misc

  IElementType ARITH_NEGATE = new BashTokenType("negation !");//||
  IElementType ARITH_BITWISE_NEGATE = new BashTokenType("bitwise negation ~");//~

  IElementType ARITH_EQ = new BashTokenType("arith ==");//==
  IElementType ARITH_NE = new BashTokenType("!=");//!=
  TokenSet arithmeticEqualityOps = TokenSet.create(ARITH_NE, ARITH_EQ);

  //arithmetic expressiong: logic
  IElementType ARITH_QMARK = new BashTokenType("?");//||
  IElementType ARITH_COLON = new BashTokenType(":");//||
  IElementType ARITH_BITWISE_XOR = new BashTokenType("^");//||
  IElementType ARITH_BITWISE_AND = new BashTokenType("&");//||
  IElementType ARITH_BITWISE_OR = new BashTokenType("|");//||

  //arithmetic operators: assign
  IElementType ARITH_ASS_MUL = new BashTokenType("*= arithmetic");// *=
  IElementType ARITH_ASS_DIV = new BashTokenType("/= arithmetic");// /=
  IElementType ARITH_ASS_MOD = new BashTokenType("%= arithmetic");// /=
  IElementType ARITH_ASS_PLUS = new BashTokenType("+= arithmetic");// /=
  IElementType ARITH_ASS_MINUS = new BashTokenType("-= arithmetic");// /=
  IElementType ARITH_ASS_SHIFT_RIGHT = new BashTokenType(">>= arithmetic");// /=
  IElementType ARITH_ASS_SHIFT_LEFT = new BashTokenType("<<= arithmetic");// /=
  IElementType ARITH_ASS_BIT_AND = new BashTokenType("&= arithmetic");// /=
  IElementType ARITH_ASS_BIT_OR = new BashTokenType("|= arithmetic");// /=
  IElementType ARITH_ASS_BIT_XOR = new BashTokenType("^= arithmetic");// /=
  //fixme missing: = ","

  TokenSet arithLiterals = TokenSet.create(NUMBER, OCTAL, HEX);

  // Special characters
  IElementType STRING_DATA = new BashTokenType("string data");
  //mapped element type
  //IElementType STRING_CONTENT = new BashTokenType("string content");

  TokenSet stringLiterals = TokenSet.create(WORD, RAW_STRING, INT, COLON);
  IElementType HEREDOC_LINE = new BashTokenType("heredoc line (temporary)");

  // test Operators
  IElementType COND_OP = new BashTokenType("cond_op");//all the test operators, e.g. -z, != ...
  IElementType COND_OP_EQ_EQ = new BashTokenType("cond_op ==");
  IElementType COND_OP_REGEX = new BashTokenType("cond_op =~");
  IElementType COND_OP_NOT = new BashTokenType("cond_op !");
  TokenSet conditionalOperators = TokenSet.create(COND_OP, OR_OR, AND_AND, BANG, COND_OP_EQ_EQ, COND_OP_REGEX, COND_OP_NOT);

  //Bash 4:
  IElementType REDIRECT_AMP_GREATER_GREATER = new BashTokenType("&>>");
  IElementType REDIRECT_AMP_GREATER = new BashTokenType("&>");

  //this must NOT include PIPE_AMP because it's a command separator and not a real redirect token
  TokenSet redirectionSet = TokenSet.create(GT, LT, SHIFT_RIGHT,
      REDIRECT_HERE_STRING, REDIRECT_LESS_GREATER,
      REDIRECT_GREATER_BAR, REDIRECT_GREATER_AMP, REDIRECT_AMP_GREATER, REDIRECT_LESS_AMP, REDIRECT_AMP_GREATER_GREATER,
      HEREDOC_MARKER_TAG);
}
