package com.jetbrains.python;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.psi.PyElementType;

public class PyTokenTypes {
  private PyTokenTypes() {
  }

  public static final PyElementType IDENTIFIER = new PyElementType("IDENTIFIER");
  public static final PyElementType LINE_BREAK = new PyElementType("LINE_BREAK");
  public static final PyElementType STATEMENT_BREAK = new PyElementType("STATEMENT_BREAK");
  public static final PyElementType SPACE = new PyElementType("SPACE");
  public static final PyElementType TAB = new PyElementType("TAB");
  public static final PyElementType FORMFEED = new PyElementType("FORMFEED");
  public static final IElementType BAD_CHARACTER = TokenType.BAD_CHARACTER;
  public static final IElementType INCONSISTENT_DEDENT = TokenType.BAD_CHARACTER;

  public static final PyElementType END_OF_LINE_COMMENT = new PyElementType("END_OF_LINE_COMMENT");

  public static final PyElementType AND_KEYWORD = new PyElementType("AND_KEYWORD");
  public static final PyElementType AS_KEYWORD = new PyElementType("AS_KEYWORD");
  public static final PyElementType ASSERT_KEYWORD = new PyElementType("ASSERT_KEYWORD");
  public static final PyElementType BREAK_KEYWORD = new PyElementType("BREAK_KEYWORD");
  public static final PyElementType CLASS_KEYWORD = new PyElementType("CLASS_KEYWORD");
  public static final PyElementType CONTINUE_KEYWORD = new PyElementType("CONTINUE_KEYWORD");
  public static final PyElementType DEF_KEYWORD = new PyElementType("DEF_KEYWORD");
  public static final PyElementType DEL_KEYWORD = new PyElementType("DEL_KEYWORD");
  public static final PyElementType ELIF_KEYWORD = new PyElementType("ELIF_KEYWORD");
  public static final PyElementType ELSE_KEYWORD = new PyElementType("ELSE_KEYWORD");
  public static final PyElementType EXCEPT_KEYWORD = new PyElementType("EXCEPT_KEYWORD");
  public static final PyElementType EXEC_KEYWORD = new PyElementType("EXEC_KEYWORD");
  public static final PyElementType FINALLY_KEYWORD = new PyElementType("FINALLY_KEYWORD");
  public static final PyElementType FOR_KEYWORD = new PyElementType("FOR_KEYWORD");
  public static final PyElementType FROM_KEYWORD = new PyElementType("FROM_KEYWORD");
  public static final PyElementType GLOBAL_KEYWORD = new PyElementType("GLOBAL_KEYWORD");
  public static final PyElementType IF_KEYWORD = new PyElementType("IF_KEYWORD");
  public static final PyElementType IMPORT_KEYWORD = new PyElementType("IMPORT_KEYWORD");
  public static final PyElementType IN_KEYWORD = new PyElementType("IN_KEYWORD");
  public static final PyElementType IS_KEYWORD = new PyElementType("IS_KEYWORD");
  public static final PyElementType LAMBDA_KEYWORD = new PyElementType("LAMBDA_KEYWORD");
  public static final PyElementType NOT_KEYWORD = new PyElementType("NOT_KEYWORD");
  public static final PyElementType OR_KEYWORD = new PyElementType("OR_KEYWORD");
  public static final PyElementType PASS_KEYWORD = new PyElementType("PASS_KEYWORD");
  public static final PyElementType PRINT_KEYWORD = new PyElementType("PRINT_KEYWORD");
  public static final PyElementType RAISE_KEYWORD = new PyElementType("RAISE_KEYWORD");
  public static final PyElementType RETURN_KEYWORD = new PyElementType("RETURN_KEYWORD");
  public static final PyElementType TRY_KEYWORD = new PyElementType("TRY_KEYWORD");
  public static final PyElementType WITH_KEYWORD = new PyElementType("WITH_KEYWORD");
  public static final PyElementType WHILE_KEYWORD = new PyElementType("WHILE_KEYWORD");
  public static final PyElementType YIELD_KEYWORD = new PyElementType("YIELD_KEYWORD");

  public static final TokenSet KEYWORDS = TokenSet.create(
      AND_KEYWORD, AS_KEYWORD, ASSERT_KEYWORD, BREAK_KEYWORD, CLASS_KEYWORD,
      CONTINUE_KEYWORD, DEF_KEYWORD, DEL_KEYWORD, ELIF_KEYWORD, ELSE_KEYWORD,
      EXCEPT_KEYWORD, EXEC_KEYWORD, FINALLY_KEYWORD, FOR_KEYWORD,
      FROM_KEYWORD,
      GLOBAL_KEYWORD, IF_KEYWORD, IMPORT_KEYWORD, IN_KEYWORD, IS_KEYWORD,
      LAMBDA_KEYWORD, NOT_KEYWORD, OR_KEYWORD, PASS_KEYWORD, PRINT_KEYWORD,
      RAISE_KEYWORD, RETURN_KEYWORD, TRY_KEYWORD, WITH_KEYWORD, WHILE_KEYWORD,
      YIELD_KEYWORD);

  public static final PyElementType INTEGER_LITERAL = new PyElementType("INTEGER_LITERAL");
  public static final PyElementType FLOAT_LITERAL = new PyElementType("FLOAT_LITERAL");
  public static final PyElementType IMAGINARY_LITERAL = new PyElementType("IMAGINARY_LITERAL");
  public static final PyElementType STRING_LITERAL = new PyElementType("STRING_LITERAL");

  // Operators
  public static final PyElementType PLUS = new PyElementType("PLUS");// +
  public static final PyElementType MINUS = new PyElementType("MINUS");// -
  public static final PyElementType MULT = new PyElementType("MULT");// *
  public static final PyElementType EXP = new PyElementType("EXP");// **
  public static final PyElementType DIV = new PyElementType("DIV"); // /
  public static final PyElementType FLOORDIV = new PyElementType("FLOORDIV"); // //
  public static final PyElementType PERC = new PyElementType("PERC");// %
  public static final PyElementType LTLT = new PyElementType("LTLT");// <<
  public static final PyElementType GTGT = new PyElementType("GTGT");// >>
  public static final PyElementType AND = new PyElementType("AND");// &
  public static final PyElementType OR = new PyElementType("OR");// |
  public static final PyElementType XOR = new PyElementType("XOR");// ^
  public static final PyElementType TILDE = new PyElementType("TILDE");// ~
  public static final PyElementType LT = new PyElementType("LT");// <
  public static final PyElementType GT = new PyElementType("GT");// >
  public static final PyElementType LE = new PyElementType("LE");// <=
  public static final PyElementType GE = new PyElementType("GE");// >=
  public static final PyElementType EQEQ = new PyElementType("EQEQ");// ==
  public static final PyElementType NE = new PyElementType("NE");// !=
  public static final PyElementType NE_OLD = new PyElementType("NE_OLD");// <>

  // Delimiters
  public static final PyElementType LPAR = new PyElementType("LPAR");// (
  public static final PyElementType RPAR = new PyElementType("RPAR");// )
  public static final PyElementType LBRACKET = new PyElementType("LBRACKET");// [
  public static final PyElementType RBRACKET = new PyElementType("RBRACKET");// ]
  public static final PyElementType LBRACE = new PyElementType("LBRACE");// {
  public static final PyElementType RBRACE = new PyElementType("RBRACE");// }
  public static final PyElementType AT = new PyElementType("AT");// @
  public static final PyElementType COMMA = new PyElementType("COMMA");// ,
  public static final PyElementType COLON = new PyElementType("COLON");// :
  public static final PyElementType DOT = new PyElementType("DOT");// .
  public static final PyElementType TICK = new PyElementType("TICK");// `
  public static final PyElementType EQ = new PyElementType("EQ");// =
  public static final PyElementType SEMICOLON = new PyElementType("SEMICOLON");// ;
  public static final PyElementType PLUSEQ = new PyElementType("PLUSEQ");// +=
  public static final PyElementType MINUSEQ = new PyElementType("MINUSEQ");// -=
  public static final PyElementType MULTEQ = new PyElementType("MULTEQ");// *=
  public static final PyElementType DIVEQ = new PyElementType("DIVEQ"); // /=
  public static final PyElementType FLOORDIVEQ = new PyElementType("FLOORDIVEQ"); // //=
  public static final PyElementType PERCEQ = new PyElementType("PERCEQ");// %=
  public static final PyElementType ANDEQ = new PyElementType("ANDEQ");// &=
  public static final PyElementType OREQ = new PyElementType("OREQ");// |=
  public static final PyElementType XOREQ = new PyElementType("XOREQ");// ^=
  public static final PyElementType LTLTEQ = new PyElementType("LTLTEQ");// <<=
  public static final PyElementType GTGTEQ = new PyElementType("GTGTEQ");// >>=
  public static final PyElementType EXPEQ = new PyElementType("EXPEQ");// **=

  public static final TokenSet OPERATIONS = TokenSet.create(
      PLUS, MINUS, MULT, EXP, DIV, FLOORDIV, PERC, LTLT, GTGT, AND, OR,
      XOR, TILDE, LT, GT, LE, GE, EQEQ, NE, NE_OLD, AT, COLON, TICK, EQ,
      PLUSEQ, MINUSEQ,
      MULTEQ, DIVEQ, FLOORDIVEQ, PERCEQ, ANDEQ, OREQ, XOREQ, LTLTEQ, GTGTEQ,
      EXPEQ);

  public static final TokenSet COMPARISON_OPERATIONS = TokenSet.create(
      LT, GT, EQEQ, GE, LE, NE, NE_OLD, IN_KEYWORD, IS_KEYWORD, NOT_KEYWORD);

  public static final TokenSet SHIFT_OPERATIONS = TokenSet.create(LTLT, GTGT);
  public static final TokenSet ADDITIVE_OPERATIONS = TokenSet.create(PLUS, MINUS);
  public static final TokenSet MULTIPLICATIVE_OPERATIONS = TokenSet.create(MULT, FLOORDIV, DIV, PERC);
  public static final TokenSet UNARY_OPERATIONS = TokenSet.create(PLUS, MINUS, TILDE);
  public static final TokenSet END_OF_STATEMENT = TokenSet.create(STATEMENT_BREAK, SEMICOLON);
  public static final TokenSet WHITESPACE = TokenSet.create(SPACE, TAB, FORMFEED);
  public static final TokenSet WHITESPACE_OR_LINEBREAK = TokenSet.create(SPACE, TAB, FORMFEED, LINE_BREAK);
  public static final TokenSet OPEN_BRACES = TokenSet.create(LBRACKET, LBRACE, LPAR);
  public static final TokenSet CLOSE_BRACES = TokenSet.create(RBRACKET, RBRACE, RPAR);

  public static final TokenSet AUG_ASSIGN_OPERATIONS = TokenSet.create(PLUSEQ, MINUSEQ, MULTEQ, DIVEQ,
      PERCEQ, EXPEQ, GTGTEQ, LTLTEQ, ANDEQ, OREQ, XOREQ);

  public static final PyElementType BACKSLASH = new PyElementType("BACKSLASH");

  public static final PyElementType INDENT = new PyElementType("INDENT");
  public static final PyElementType DEDENT = new PyElementType("DEDENT");
}
