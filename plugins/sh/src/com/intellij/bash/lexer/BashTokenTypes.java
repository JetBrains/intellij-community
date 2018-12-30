package com.intellij.bash.lexer;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;


public interface BashTokenTypes {
  IElementType BAD_CHARACTER = TokenType.BAD_CHARACTER;

  // common types
  IElementType WHITESPACE = TokenType.WHITE_SPACE;
  IElementType LINE_CONTINUATION = new BashTokenType("line continuation \\");
  TokenSet whitespaceTokens = TokenSet.create(WHITESPACE, LINE_CONTINUATION);

  IElementType ARITH_NUMBER = new BashTokenType("number");
  IElementType WORD = new BashTokenType("word");
  IElementType ASSIGNMENT_WORD = new BashTokenType("assignment_word"); //"a" =2
  IElementType DOLLAR = new BashTokenType("$");

  IElementType LEFT_PAREN = new BashTokenType("(");
  IElementType RIGHT_PAREN = new BashTokenType(")");

  IElementType LEFT_CURLY = new BashTokenType("{");
  IElementType RIGHT_CURLY = new BashTokenType("}");

  IElementType LEFT_SQUARE = new BashTokenType("[ (left square)");
  IElementType RIGHT_SQUARE = new BashTokenType("] (right square)");
  TokenSet bracketSet = TokenSet.create(LEFT_SQUARE, RIGHT_SQUARE);

  // comments
  IElementType COMMENT = new BashTokenType("Comment");
  IElementType SHEBANG = new BashTokenType("Shebang");

  TokenSet commentTokens = TokenSet.create(COMMENT);

  // bash reserved keywords, in alphabetic order
  IElementType BANG_TOKEN = new BashTokenType("!");
  IElementType CASE_KEYWORD = new BashTokenType("case");
  IElementType DO_KEYWORD = new BashTokenType("do");
  IElementType DONE_KEYWORD = new BashTokenType("done");
  IElementType ELIF_KEYWORD = new BashTokenType("elif");
  IElementType ELSE_KEYWORD = new BashTokenType("else");
  IElementType ESAC_KEYWORD = new BashTokenType("esac");
  IElementType FI_KEYWORD = new BashTokenType("fi");
  IElementType FOR_KEYWORD = new BashTokenType("for");
  IElementType FUNCTION_KEYWORD = new BashTokenType("function");
  IElementType IF_KEYWORD = new BashTokenType("if");
  IElementType IN_KEYWORD_REMAPPED = new BashTokenType("in");
  IElementType SELECT_KEYWORD = new BashTokenType("select");
  IElementType THEN_KEYWORD = new BashTokenType("then");
  IElementType UNTIL_KEYWORD = new BashTokenType("until");
  IElementType WHILE_KEYWORD = new BashTokenType("while");
  IElementType TIME_KEYWORD = new BashTokenType("time");
  IElementType TRAP_KEYWORD = new BashTokenType("trap");
  IElementType LET_KEYWORD = new BashTokenType("let");
  IElementType BRACKET_KEYWORD = new BashTokenType("[[ (left bracket)");
  IElementType _BRACKET_KEYWORD = new BashTokenType("]] (right bracket)");

  //case state
  IElementType CASE_END = new BashTokenType(";;");//;; for case expressions

  // arithmetic expressions
  IElementType EXPR_ARITH = new BashTokenType("((");//))
  IElementType _EXPR_ARITH = new BashTokenType("))");//]] after a $((
  IElementType EXPR_ARITH_SQUARE = new BashTokenType("[ for arithmetic");
  IElementType _EXPR_ARITH_SQUARE = new BashTokenType("] for arithmetic");

  //conditional expressions
  IElementType EXPR_CONDITIONAL = new BashTokenType("[ (left conditional)");//"[ "
  IElementType _EXPR_CONDITIONAL = new BashTokenType(" ] (right conditional)");//" ]"

  TokenSet keywords = TokenSet.create(BANG_TOKEN, CASE_KEYWORD, DO_KEYWORD, DONE_KEYWORD,
      ELIF_KEYWORD, ELSE_KEYWORD, ESAC_KEYWORD, FI_KEYWORD, FOR_KEYWORD, FUNCTION_KEYWORD,
      IF_KEYWORD, IN_KEYWORD_REMAPPED, SELECT_KEYWORD, THEN_KEYWORD, UNTIL_KEYWORD, WHILE_KEYWORD,
      TIME_KEYWORD, BRACKET_KEYWORD, _BRACKET_KEYWORD,
      CASE_END, DOLLAR,
      EXPR_ARITH, _EXPR_ARITH, EXPR_CONDITIONAL, _EXPR_CONDITIONAL);

  TokenSet internalCommands = TokenSet.create(TRAP_KEYWORD, LET_KEYWORD);

  //these are keyword tokens which may be used as identifiers, e.g. in a for loop
  //these tokens will be remapped to word tokens if they occur at a position where a word token would be accepted
  TokenSet identifierKeywords = TokenSet.create(
      CASE_KEYWORD, DO_KEYWORD, DONE_KEYWORD, ELIF_KEYWORD, ELSE_KEYWORD, ESAC_KEYWORD, FI_KEYWORD, FOR_KEYWORD, FUNCTION_KEYWORD,
      IF_KEYWORD, IN_KEYWORD_REMAPPED, SELECT_KEYWORD, THEN_KEYWORD, UNTIL_KEYWORD, WHILE_KEYWORD, TIME_KEYWORD
  );

  // single characters
  IElementType BACKSLASH = new BashTokenType("\\");
  IElementType AMP = new BashTokenType("&");
  IElementType AT = new BashTokenType("@");
  IElementType COLON = new BashTokenType(":");
  IElementType COMMA = new BashTokenType(",");
  IElementType EQ = new BashTokenType("=");
  IElementType ADD_EQ = new BashTokenType("+=");
  IElementType SEMI = new BashTokenType(";");
  IElementType SHIFT_RIGHT = new BashTokenType(">>");//>>
  IElementType LESS_THAN = new BashTokenType("<");//>>
  IElementType GREATER_THAN = new BashTokenType(">");//>>

  IElementType PIPE = new BashTokenType("|");// }
  IElementType PIPE_AMP = new BashTokenType("|&"); //bash 4 only, equivalent to 2>&1 |
  IElementType AND_AND = new BashTokenType("&&");//!=
  IElementType OR_OR = new BashTokenType("||");//!=

  IElementType LINE_FEED = new BashTokenType("linefeed");

  TokenSet pipeTokens = TokenSet.create(PIPE, PIPE_AMP);

  //arithmetic operators: plus
  IElementType ARITH_PLUS_PLUS = new BashTokenType("++");//++
  IElementType ARITH_PLUS = new BashTokenType("+");//+

  //arithmetic operators: minus
  IElementType ARITH_MINUS_MINUS = new BashTokenType("--");//++
  IElementType ARITH_MINUS = new BashTokenType("-");//+

  TokenSet arithmeticPostOps = TokenSet.create(ARITH_PLUS_PLUS, ARITH_MINUS_MINUS);
  TokenSet arithmeticPreOps = TokenSet.create(ARITH_PLUS_PLUS, ARITH_MINUS_MINUS);
  TokenSet arithmeticAdditionOps = TokenSet.create(ARITH_PLUS, ARITH_MINUS);

  //arithmetic operators: misc
  IElementType ARITH_EXPONENT = new BashTokenType("**");//**
  IElementType ARITH_MULT = new BashTokenType("*");//*
  IElementType ARITH_DIV = new BashTokenType("/");// /
  IElementType ARITH_MOD = new BashTokenType("%");//%
  IElementType ARITH_SHIFT_LEFT = new BashTokenType("<<");//<<
  IElementType ARITH_SHIFT_RIGHT = new BashTokenType(">>");//>>
  IElementType ARITH_NEGATE = new BashTokenType("negation !");//||
  IElementType ARITH_BITWISE_NEGATE = new BashTokenType("bitwise negation ~");//~

  TokenSet arithmeticShiftOps = TokenSet.create(ARITH_SHIFT_LEFT, ARITH_SHIFT_RIGHT);

  TokenSet arithmeticNegationOps = TokenSet.create(ARITH_NEGATE, ARITH_BITWISE_NEGATE);

  TokenSet arithmeticProduct = TokenSet.create(ARITH_MULT, ARITH_DIV, ARITH_MOD);

  //arithmetic operators: comparision
  IElementType ARITH_LE = new BashTokenType("<=");//<=
  IElementType ARITH_GE = new BashTokenType(">=");//>=
  IElementType ARITH_GT = new BashTokenType("arith >");//>=
  IElementType ARITH_LT = new BashTokenType("arith <");//>=

  TokenSet arithmeticCmpOp = TokenSet.create(ARITH_LE, ARITH_GE, ARITH_LT, ARITH_GT);

  IElementType ARITH_EQ = new BashTokenType("arith ==");//==
  IElementType ARITH_NE = new BashTokenType("!=");//!=
  TokenSet arithmeticEqualityOps = TokenSet.create(ARITH_NE, ARITH_EQ);

  //arithmetic expressiong: logic
  IElementType ARITH_QMARK = new BashTokenType("?");//||
  IElementType ARITH_COLON = new BashTokenType(":");//||
  IElementType ARITH_BITWISE_XOR = new BashTokenType("^");//||
  IElementType ARITH_BITWISE_AND = new BashTokenType("&");//||
  //fixme missing: |

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

  TokenSet arithmeticAssign = TokenSet.create(ARITH_ASS_MUL, ARITH_ASS_DIV, ARITH_ASS_MOD, ARITH_ASS_PLUS,
      ARITH_ASS_MINUS, ARITH_ASS_SHIFT_LEFT, ARITH_ASS_SHIFT_RIGHT,
      ARITH_ASS_BIT_AND, ARITH_ASS_BIT_OR, ARITH_ASS_BIT_XOR);

  //arithmetic literals
  IElementType ARITH_HEX_NUMBER = new BashTokenType("0x hex literal");
  IElementType ARITH_OCTAL_NUMBER = new BashTokenType("octal literal");

  IElementType ARITH_BASE_CHAR = new BashTokenType("arithmetic base char (#)");

  TokenSet arithLiterals = TokenSet.create(ARITH_NUMBER, ARITH_OCTAL_NUMBER, ARITH_HEX_NUMBER);

  //builtin command
  IElementType COMMAND_TOKEN = new BashTokenType("command");//!=
  TokenSet commands = TokenSet.create(COMMAND_TOKEN);

  //variables
  IElementType VARIABLE = new BashTokenType("variable");

  //parameter expansion
  IElementType PARAM_EXPANSION_OP_UNKNOWN = new BashTokenType("Parameter expansion operator (unknown)");
  IElementType PARAM_EXPANSION_OP_EXCL = new BashTokenType("Parameter expansion operator '!'");
  IElementType PARAM_EXPANSION_OP_COLON_EQ = new BashTokenType("Parameter expansion operator ':='");
  IElementType PARAM_EXPANSION_OP_COLON_QMARK = new BashTokenType("Parameter expansion operator ':?'");
  IElementType PARAM_EXPANSION_OP_EQ = new BashTokenType("Parameter expansion operator '='");
  IElementType PARAM_EXPANSION_OP_COLON = new BashTokenType("Parameter expansion operator ':'");
  IElementType PARAM_EXPANSION_OP_COLON_MINUS = new BashTokenType("Parameter expansion operator ':-'");
  IElementType PARAM_EXPANSION_OP_MINUS = new BashTokenType("Parameter expansion operator '-'");
  IElementType PARAM_EXPANSION_OP_COLON_PLUS = new BashTokenType("Parameter expansion operator ':+'");
  IElementType PARAM_EXPANSION_OP_PLUS = new BashTokenType("Parameter expansion operator '+'");
  IElementType PARAM_EXPANSION_OP_HASH = new BashTokenType("Parameter expansion operator '#'");
  IElementType PARAM_EXPANSION_OP_HASH_HASH = new BashTokenType("Parameter expansion operator '##'");
  IElementType PARAM_EXPANSION_OP_AT = new BashTokenType("Parameter expansion operator '@'");
  IElementType PARAM_EXPANSION_OP_STAR = new BashTokenType("Parameter expansion operator '*'");
  IElementType PARAM_EXPANSION_OP_QMARK = new BashTokenType("Parameter expansion operator '?'");
  IElementType PARAM_EXPANSION_OP_DOT = new BashTokenType("Parameter expansion operator '.'");
  IElementType PARAM_EXPANSION_OP_PERCENT = new BashTokenType("Parameter expansion operator '%'");
  IElementType PARAM_EXPANSION_OP_SLASH = new BashTokenType("Parameter expansion operator '/'");
  IElementType PARAM_EXPANSION_OP_SLASH_SLASH = new BashTokenType("Parameter expansion operator '//'");
  IElementType PARAM_EXPANSION_OP_LOWERCASE_FIRST = new BashTokenType("Parameter expansion operator ','");
  IElementType PARAM_EXPANSION_OP_LOWERCASE_ALL = new BashTokenType("Parameter expansion operator ',,'");
  IElementType PARAM_EXPANSION_OP_UPPERCASE_FIRST = new BashTokenType("Parameter expansion operator '^'");
  IElementType PARAM_EXPANSION_OP_UPPERCASE_ALL = new BashTokenType("Parameter expansion operator '^^'");
  IElementType PARAM_EXPANSION_PATTERN = new BashTokenType("Parameter expansion regex pattern");
  TokenSet paramExpansionOperators = TokenSet.create(PARAM_EXPANSION_OP_UNKNOWN, PARAM_EXPANSION_OP_EXCL,
      PARAM_EXPANSION_OP_COLON_EQ, PARAM_EXPANSION_OP_COLON_QMARK, PARAM_EXPANSION_OP_EQ, PARAM_EXPANSION_OP_COLON, PARAM_EXPANSION_OP_COLON_MINUS,
      PARAM_EXPANSION_OP_MINUS, PARAM_EXPANSION_OP_PLUS, PARAM_EXPANSION_OP_COLON_PLUS, PARAM_EXPANSION_OP_HASH, PARAM_EXPANSION_OP_HASH_HASH,
      PARAM_EXPANSION_OP_AT, PARAM_EXPANSION_OP_STAR, PARAM_EXPANSION_OP_PERCENT, PARAM_EXPANSION_OP_QMARK, PARAM_EXPANSION_OP_DOT,
      PARAM_EXPANSION_OP_SLASH, PARAM_EXPANSION_OP_SLASH_SLASH,
      PARAM_EXPANSION_OP_LOWERCASE_ALL, PARAM_EXPANSION_OP_LOWERCASE_FIRST,
      PARAM_EXPANSION_OP_UPPERCASE_ALL, PARAM_EXPANSION_OP_UPPERCASE_FIRST,
      PARAM_EXPANSION_PATTERN);
  TokenSet paramExpansionAssignmentOps = TokenSet.create(PARAM_EXPANSION_OP_EQ, PARAM_EXPANSION_OP_COLON_EQ);


  // Special characters
  IElementType STRING_BEGIN = new BashTokenType("string begin");
  IElementType STRING_DATA = new BashTokenType("string data");
  IElementType STRING_END = new BashTokenType("string end");
  //mapped element type
  IElementType STRING_CONTENT = new BashTokenType("string content");

  IElementType STRING2 = new BashTokenType("unevaluated string (STRING2)");
  IElementType BACKQUOTE = new BashTokenType("backquote `");

  IElementType INTEGER_LITERAL = new BashTokenType("int literal");

  TokenSet stringLiterals = TokenSet.create(WORD, STRING2, INTEGER_LITERAL, COLON);

  IElementType HEREDOC_MARKER_TAG = new BashTokenType("heredoc marker tag");
  IElementType HEREDOC_MARKER_START = new BashTokenType("heredoc start marker");
  IElementType HEREDOC_MARKER_END = new BashTokenType("heredoc end marker");
  IElementType HEREDOC_MARKER_IGNORING_TABS_END = new BashTokenType("heredoc end marker (ignoring tabs)");
  IElementType HEREDOC_LINE = new BashTokenType("heredoc line (temporary)");
  IElementType HEREDOC_CONTENT = new BashTokenType("here doc content");

  // test Operators
  IElementType COND_OP = new BashTokenType("cond_op");//all the test operators, e.g. -z, != ...
  IElementType COND_OP_EQ_EQ = new BashTokenType("cond_op ==");
  IElementType COND_OP_REGEX = new BashTokenType("cond_op =~");
  IElementType COND_OP_NOT = new BashTokenType("cond_op !");
  TokenSet conditionalOperators = TokenSet.create(COND_OP, OR_OR, AND_AND, BANG_TOKEN, COND_OP_EQ_EQ, COND_OP_REGEX);

  //redirects
  IElementType REDIRECT_HERE_STRING = new BashTokenType("<<<");
  IElementType REDIRECT_LESS_AMP = new BashTokenType("<&");
  IElementType REDIRECT_GREATER_AMP = new BashTokenType(">&");
  IElementType REDIRECT_LESS_GREATER = new BashTokenType("<>");
  IElementType REDIRECT_GREATER_BAR = new BashTokenType(">|");
  IElementType FILEDESCRIPTOR = new BashTokenType("&[0-9] filedescriptor");

  //Bash 4:
  IElementType REDIRECT_AMP_GREATER_GREATER = new BashTokenType("&>>");
  IElementType REDIRECT_AMP_GREATER = new BashTokenType("&>");

  //this must NOT include PIPE_AMP because it's a command separator and not a real redirect token
  TokenSet redirectionSet = TokenSet.create(GREATER_THAN, LESS_THAN, SHIFT_RIGHT,
      REDIRECT_HERE_STRING, REDIRECT_LESS_GREATER,
      REDIRECT_GREATER_BAR, REDIRECT_GREATER_AMP, REDIRECT_AMP_GREATER, REDIRECT_LESS_AMP, REDIRECT_AMP_GREATER_GREATER,
      HEREDOC_MARKER_TAG);

  //sets
  TokenSet EQ_SET = TokenSet.create(EQ);
}
