/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
  public static final PyElementType INCONSISTENT_DEDENT = new PyElementType("INCONSISTENT_DEDENT");

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
  public static final PyElementType IN_KEYWORD = new PyElementType("IN_KEYWORD", "__contains__");
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

  // new keywords in Python 3
  public static final PyElementType NONE_KEYWORD = new PyElementType("NONE_KEYWORD");
  public static final PyElementType TRUE_KEYWORD = new PyElementType("TRUE_KEYWORD");
  public static final PyElementType FALSE_KEYWORD = new PyElementType("FALSE_KEYWORD");
  public static final PyElementType NONLOCAL_KEYWORD = new PyElementType("NONLOCAL_KEYWORD");
  public static final PyElementType DEBUG_KEYWORD = new PyElementType("DEBUG_KEYWORD");
  public static final PyElementType ASYNC_KEYWORD = new PyElementType("ASYNC_KEYWORD");
  public static final PyElementType AWAIT_KEYWORD = new PyElementType("AWAIT_KEYWORD", "__await__");

  public static final PyElementType INTEGER_LITERAL = new PyElementType("INTEGER_LITERAL");
  public static final PyElementType FLOAT_LITERAL = new PyElementType("FLOAT_LITERAL");
  public static final PyElementType IMAGINARY_LITERAL = new PyElementType("IMAGINARY_LITERAL");

  public static final PyElementType SINGLE_QUOTED_STRING = new PyElementType("SINGLE_QUOTED_STRING");
  public static final PyElementType TRIPLE_QUOTED_STRING = new PyElementType("TRIPLE_QUOTED_STRING");
  public static final PyElementType SINGLE_QUOTED_UNICODE = new PyElementType("SINGLE_QUOTED_UNICODE");
  public static final PyElementType TRIPLE_QUOTED_UNICODE = new PyElementType("TRIPLE_QUOTED_UNICODE");

  public static final PyElementType DOCSTRING = new PyElementType("DOCSTRING");

  public static final TokenSet UNICODE_NODES = TokenSet.create(TRIPLE_QUOTED_UNICODE, SINGLE_QUOTED_UNICODE);
  public static final TokenSet TRIPLE_NODES = TokenSet.create(TRIPLE_QUOTED_UNICODE, TRIPLE_QUOTED_STRING);
  public static final TokenSet STRING_NODES = TokenSet.orSet(UNICODE_NODES, TokenSet.create(SINGLE_QUOTED_STRING,
                                                             TRIPLE_QUOTED_STRING, DOCSTRING));
  // Operators
  public static final PyElementType PLUS = new PyElementType("PLUS", "__add__");// +
  public static final PyElementType MINUS = new PyElementType("MINUS", "__sub__");// -
  public static final PyElementType MULT = new PyElementType("MULT", "__mul__");// *
  public static final PyElementType EXP = new PyElementType("EXP", "__pow__");// **
  public static final PyElementType DIV = new PyElementType("DIV", "__div__"); // /
  public static final PyElementType FLOORDIV = new PyElementType("FLOORDIV", "__floordiv__"); // //
  public static final PyElementType PERC = new PyElementType("PERC", "__mod__");// %
  public static final PyElementType LTLT = new PyElementType("LTLT", "__lshift__");// <<
  public static final PyElementType GTGT = new PyElementType("GTGT", "__rshift__");// >>
  public static final PyElementType AND = new PyElementType("AND", "__and__");// &
  public static final PyElementType OR = new PyElementType("OR", "__or__");// |
  public static final PyElementType XOR = new PyElementType("XOR", "__xor__");// ^
  public static final PyElementType TILDE = new PyElementType("TILDE", "__invert__");// ~
  public static final PyElementType LT = new PyElementType("LT", "__lt__");// <
  public static final PyElementType GT = new PyElementType("GT", "__gt__");// >
  public static final PyElementType LE = new PyElementType("LE", "__le__");// <=
  public static final PyElementType GE = new PyElementType("GE", "__ge__");// >=
  public static final PyElementType EQEQ = new PyElementType("EQEQ", "__eq__");// ==
  public static final PyElementType NE = new PyElementType("NE", "__ne__");// !=
  public static final PyElementType NE_OLD = new PyElementType("NE_OLD", "__ne__");// <>

  // Delimiters
  public static final PyElementType LPAR = new PyElementType("LPAR");// (
  public static final PyElementType RPAR = new PyElementType("RPAR");// )
  public static final PyElementType LBRACKET = new PyElementType("LBRACKET");// [
  public static final PyElementType RBRACKET = new PyElementType("RBRACKET");// ]
  public static final PyElementType LBRACE = new PyElementType("LBRACE");// {
  public static final PyElementType RBRACE = new PyElementType("RBRACE");// }
  public static final PyElementType AT = new PyElementType("AT", "__matmul__");// @
  public static final PyElementType COMMA = new PyElementType("COMMA");// ,
  public static final PyElementType COLON = new PyElementType("COLON");// :
  public static final PyElementType DOT = new PyElementType("DOT");// .
  public static final PyElementType TICK = new PyElementType("TICK");// `
  public static final PyElementType EQ = new PyElementType("EQ");// =
  public static final PyElementType SEMICOLON = new PyElementType("SEMICOLON");// ;
  public static final PyElementType PLUSEQ = new PyElementType("PLUSEQ");// +=
  public static final PyElementType MINUSEQ = new PyElementType("MINUSEQ");// -=
  public static final PyElementType MULTEQ = new PyElementType("MULTEQ");// *=
  public static final PyElementType ATEQ = new PyElementType("ATEQ"); // @=
  public static final PyElementType DIVEQ = new PyElementType("DIVEQ"); // /=
  public static final PyElementType FLOORDIVEQ = new PyElementType("FLOORDIVEQ"); // //=
  public static final PyElementType PERCEQ = new PyElementType("PERCEQ");// %=
  public static final PyElementType ANDEQ = new PyElementType("ANDEQ");// &=
  public static final PyElementType OREQ = new PyElementType("OREQ");// |=
  public static final PyElementType XOREQ = new PyElementType("XOREQ");// ^=
  public static final PyElementType LTLTEQ = new PyElementType("LTLTEQ");// <<=
  public static final PyElementType GTGTEQ = new PyElementType("GTGTEQ");// >>=
  public static final PyElementType EXPEQ = new PyElementType("EXPEQ");// **=
  public static final PyElementType RARROW = new PyElementType("RARROW");// ->

  public static final TokenSet OPERATIONS = TokenSet.create(
      PLUS, MINUS, MULT, AT, EXP, DIV, FLOORDIV, PERC, LTLT, GTGT, AND, OR,
      XOR, TILDE, LT, GT, LE, GE, EQEQ, NE, NE_OLD, AT, COLON, TICK, EQ,
      PLUSEQ, MINUSEQ,
      MULTEQ, ATEQ, DIVEQ, FLOORDIVEQ, PERCEQ, ANDEQ, OREQ, XOREQ, LTLTEQ, GTGTEQ,
      EXPEQ);

  public static final TokenSet COMPARISON_OPERATIONS = TokenSet.create(
      LT, GT, EQEQ, GE, LE, NE, NE_OLD, IN_KEYWORD, IS_KEYWORD, NOT_KEYWORD);

  public static final TokenSet SHIFT_OPERATIONS = TokenSet.create(LTLT, GTGT);
  public static final TokenSet ADDITIVE_OPERATIONS = TokenSet.create(PLUS, MINUS);
  public static final TokenSet MULTIPLICATIVE_OPERATIONS = TokenSet.create(MULT, AT, FLOORDIV, DIV, PERC);
  public static final TokenSet STAR_OPERATORS = TokenSet.create(MULT, EXP);
  public static final TokenSet UNARY_OPERATIONS = TokenSet.create(PLUS, MINUS, TILDE);
  public static final TokenSet BITWISE_OPERATIONS = TokenSet.create(AND, OR, XOR); 
  public static final TokenSet EQUALITY_OPERATIONS = TokenSet.create(EQEQ, NE, NE_OLD);
  public static final TokenSet RELATIONAL_OPERATIONS = TokenSet.create(LT, GT, LE, GE);
  public static final TokenSet END_OF_STATEMENT = TokenSet.create(STATEMENT_BREAK, SEMICOLON);
  public static final TokenSet WHITESPACE = TokenSet.create(SPACE, TAB, FORMFEED);
  public static final TokenSet WHITESPACE_OR_LINEBREAK = TokenSet.create(SPACE, TAB, FORMFEED, LINE_BREAK);
  public static final TokenSet OPEN_BRACES = TokenSet.create(LBRACKET, LBRACE, LPAR);
  public static final TokenSet CLOSE_BRACES = TokenSet.create(RBRACKET, RBRACE, RPAR);
  
  public static final TokenSet NUMERIC_LITERALS = TokenSet.create(FLOAT_LITERAL, INTEGER_LITERAL, IMAGINARY_LITERAL);
  public static final TokenSet BOOL_LITERALS = TokenSet.create(TRUE_KEYWORD, FALSE_KEYWORD);
  public static final TokenSet SCALAR_LITERALS = TokenSet.orSet(BOOL_LITERALS, NUMERIC_LITERALS, TokenSet.create(NONE_KEYWORD));
  public static final TokenSet EXPRESSION_KEYWORDS = TokenSet.create(TRUE_KEYWORD, FALSE_KEYWORD, NONE_KEYWORD);

  public static final TokenSet AUG_ASSIGN_OPERATIONS = TokenSet.create(PLUSEQ, MINUSEQ, MULTEQ, ATEQ, DIVEQ,
      PERCEQ, EXPEQ, GTGTEQ, LTLTEQ, ANDEQ, OREQ, XOREQ, FLOORDIVEQ);

  public static final PyElementType BACKSLASH = new PyElementType("BACKSLASH");

  public static final PyElementType INDENT = new PyElementType("INDENT");
  public static final PyElementType DEDENT = new PyElementType("DEDENT");
}
