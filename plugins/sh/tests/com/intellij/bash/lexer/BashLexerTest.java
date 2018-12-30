/*
 * Copyright (c) Joachim Ansorg, mail@ansorg-it.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.bash.lexer;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.intellij.bash.BashVersion;
import com.intellij.psi.tree.IElementType;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.StringReader;
import java.util.List;

import static com.intellij.bash.lexer.BashTokenTypes.*;
import static junit.framework.TestCase.assertNotSame;

public class BashLexerTest {
  @Test
  public void testInitialState() {
    _BashLexer lexer = new _BashLexer(BashVersion.V3, new StringReader("abc"));
    Assert.assertTrue(lexer.isInState(_BashLexer.YYINITIAL));
  }

  @Test
  public void testAfterStringState() {
    _BashLexer lexer = new _BashLexer(BashVersion.V3, new StringReader("\"abc"));
    Assert.assertTrue(lexer.isInState(_BashLexer.YYINITIAL));
  }

  @Test
  public void testSimpleDefTokenization() {
    testTokenization("#", COMMENT);
    testTokenization("# ", COMMENT);
    testTokenization("# Text", COMMENT);
    testTokenization("# Text\n", COMMENT, LINE_FEED);
    testTokenization("a #!b", WORD, WHITESPACE, SHEBANG);
    testTokenization("#a\n#b\n", COMMENT, LINE_FEED, COMMENT, LINE_FEED);
  }

  @Test
  public void testVariables() {
    testTokenization("$abc", VARIABLE);

    testTokenization("$a$", VARIABLE, DOLLAR);
    testTokenization("$", DOLLAR);
    testTokenization("$-", VARIABLE);
    testTokenization("$_", VARIABLE);
    testTokenization("$@", VARIABLE);
    testTokenization("$*", VARIABLE);
    testTokenization("$?", VARIABLE);

    testTokenization("$(echo)", DOLLAR, LEFT_PAREN, WORD, RIGHT_PAREN);
    testTokenization("${echo}", DOLLAR, LEFT_CURLY, WORD, RIGHT_CURLY);
    testTokenization("${_a}", DOLLAR, LEFT_CURLY, WORD, RIGHT_CURLY);
    testTokenization("${#echo}", DOLLAR, LEFT_CURLY, PARAM_EXPANSION_OP_HASH, WORD, RIGHT_CURLY);
    testTokenization("${!echo}", DOLLAR, LEFT_CURLY, PARAM_EXPANSION_OP_EXCL, WORD, RIGHT_CURLY);
    testTokenization("a ${a# echo} a", WORD, WHITESPACE, DOLLAR, LEFT_CURLY, WORD, PARAM_EXPANSION_OP_HASH, WHITESPACE, WORD, RIGHT_CURLY, WHITESPACE, WORD);
    testTokenization("a ${a## echo} a", WORD, WHITESPACE, DOLLAR, LEFT_CURLY, WORD, PARAM_EXPANSION_OP_HASH_HASH, WHITESPACE, WORD, RIGHT_CURLY, WHITESPACE, WORD);
    testTokenization("${echo} ${echo}", DOLLAR, LEFT_CURLY, WORD, RIGHT_CURLY, WHITESPACE, DOLLAR, LEFT_CURLY, WORD, RIGHT_CURLY);
    testTokenization("a=1 b=2 echo", ASSIGNMENT_WORD, EQ, INTEGER_LITERAL, WHITESPACE, ASSIGNMENT_WORD, EQ, INTEGER_LITERAL, WHITESPACE, WORD);
    testTokenization("a=1 b=2", ASSIGNMENT_WORD, EQ, INTEGER_LITERAL, WHITESPACE, ASSIGNMENT_WORD, EQ, INTEGER_LITERAL);
    testTokenization("a+=a", ASSIGNMENT_WORD, ADD_EQ, WORD);
    testTokenization("if a; then PIDDIR=a$(a) a; fi", IF_KEYWORD, WHITESPACE, WORD, SEMI, WHITESPACE, THEN_KEYWORD, WHITESPACE, ASSIGNMENT_WORD, EQ, WORD, DOLLAR, LEFT_PAREN, WORD, RIGHT_PAREN, WHITESPACE, WORD, SEMI, WHITESPACE, FI_KEYWORD);

    //line continuation token is ignored
    testTokenization("a=a\\\nb", ASSIGNMENT_WORD, EQ, WORD);

    testTokenization("[ $(uname -a) ]", EXPR_CONDITIONAL, DOLLAR, LEFT_PAREN, WORD, WHITESPACE, WORD, RIGHT_PAREN, _EXPR_CONDITIONAL);
  }

  @Test
  public void testArrayVariables() {
    testTokenization("${PIPESTATUS[0]}", DOLLAR, LEFT_CURLY, WORD, LEFT_SQUARE, ARITH_NUMBER, RIGHT_SQUARE, RIGHT_CURLY);

    testTokenization("${#myVar[*]}", DOLLAR, LEFT_CURLY, PARAM_EXPANSION_OP_HASH, WORD, LEFT_SQUARE, PARAM_EXPANSION_OP_STAR, RIGHT_SQUARE, RIGHT_CURLY);

    testTokenization("${myVar[0]:1}", DOLLAR, LEFT_CURLY, WORD, LEFT_SQUARE, ARITH_NUMBER, RIGHT_SQUARE, PARAM_EXPANSION_OP_COLON, WORD, RIGHT_CURLY);

    testTokenization("${myVar[*]:1}", DOLLAR, LEFT_CURLY, WORD, LEFT_SQUARE, PARAM_EXPANSION_OP_STAR, RIGHT_SQUARE, PARAM_EXPANSION_OP_COLON, WORD, RIGHT_CURLY);

    testTokenization("${myVar[@]:1}", DOLLAR, LEFT_CURLY, WORD, LEFT_SQUARE, PARAM_EXPANSION_OP_AT, RIGHT_SQUARE, PARAM_EXPANSION_OP_COLON, WORD, RIGHT_CURLY);

    testTokenization("a=( one two three)", ASSIGNMENT_WORD, EQ, LEFT_PAREN, WHITESPACE, WORD, WHITESPACE, WORD, WHITESPACE, WORD, RIGHT_PAREN);

    testTokenization("a=( one two [2]=three)", ASSIGNMENT_WORD, EQ, LEFT_PAREN, WHITESPACE, WORD, WHITESPACE, WORD, WHITESPACE, LEFT_SQUARE, ARITH_NUMBER, RIGHT_SQUARE, EQ, WORD, RIGHT_PAREN);

    testTokenization("a=(1 2 3)", ASSIGNMENT_WORD, EQ, LEFT_PAREN, WORD, WHITESPACE, WORD, WHITESPACE, WORD, RIGHT_PAREN);

    testTokenization("a[1]=", ASSIGNMENT_WORD, LEFT_SQUARE, ARITH_NUMBER, RIGHT_SQUARE, EQ);
  }

  @Test
  public void testArrayWithString() {
    // ARR=(['foo']='someval' ['bar']='otherval')

    testTokenization("a=(['x']=1)", ASSIGNMENT_WORD, EQ, LEFT_PAREN, LEFT_SQUARE, STRING2, RIGHT_SQUARE, EQ, WORD, RIGHT_PAREN);
  }

  @Test
  public void testSquareBracketArithmeticExpr() {
    testTokenization("$[1]", DOLLAR, EXPR_ARITH_SQUARE, ARITH_NUMBER, _EXPR_ARITH_SQUARE);

    testTokenization("$[1 ]", DOLLAR, EXPR_ARITH_SQUARE, ARITH_NUMBER, WHITESPACE, _EXPR_ARITH_SQUARE);

    testTokenization("$[1/${a}]", DOLLAR, EXPR_ARITH_SQUARE, ARITH_NUMBER, ARITH_DIV, DOLLAR, LEFT_CURLY, WORD, RIGHT_CURLY, _EXPR_ARITH_SQUARE);

    //lexable, but bad syntax
    testTokenization("$(([1]))", DOLLAR, EXPR_ARITH, LEFT_SQUARE, ARITH_NUMBER, RIGHT_SQUARE, _EXPR_ARITH);
  }

  @Test
  public void testArithmeticExpr() {
    testTokenization("$((1))", DOLLAR, EXPR_ARITH, ARITH_NUMBER, _EXPR_ARITH);

    testTokenization("$((1,1))", DOLLAR, EXPR_ARITH, ARITH_NUMBER, COMMA, ARITH_NUMBER, _EXPR_ARITH);

    testTokenization("$((1/${a}))", DOLLAR, EXPR_ARITH, ARITH_NUMBER, ARITH_DIV, DOLLAR, LEFT_CURLY, WORD, RIGHT_CURLY, _EXPR_ARITH);

    testTokenization("$((a=1,1))", DOLLAR, EXPR_ARITH, ASSIGNMENT_WORD, EQ, ARITH_NUMBER, COMMA, ARITH_NUMBER, _EXPR_ARITH);

    testTokenization("$((-1))", DOLLAR, EXPR_ARITH, ARITH_MINUS, ARITH_NUMBER, _EXPR_ARITH);

    testTokenization("$((--1))", DOLLAR, EXPR_ARITH, ARITH_MINUS, ARITH_MINUS, ARITH_NUMBER, _EXPR_ARITH);

    testTokenization("$((--a))", DOLLAR, EXPR_ARITH, ARITH_MINUS_MINUS, WORD, _EXPR_ARITH);

    testTokenization("$((- --a))", DOLLAR, EXPR_ARITH, ARITH_MINUS, WHITESPACE, ARITH_MINUS_MINUS, WORD, _EXPR_ARITH);

    testTokenization("$((-1 -1))", DOLLAR, EXPR_ARITH, ARITH_MINUS, ARITH_NUMBER, WHITESPACE, ARITH_MINUS, ARITH_NUMBER, _EXPR_ARITH);

    testTokenization("$((a & b))", DOLLAR, EXPR_ARITH, WORD, WHITESPACE, ARITH_BITWISE_AND, WHITESPACE, WORD, _EXPR_ARITH);

    testTokenization("$((a && b))", DOLLAR, EXPR_ARITH, WORD, WHITESPACE, AND_AND, WHITESPACE, WORD, _EXPR_ARITH);

    testTokenization("$((a || b))", DOLLAR, EXPR_ARITH, WORD, WHITESPACE, OR_OR, WHITESPACE, WORD, _EXPR_ARITH);

    testTokenization("$((!a))", DOLLAR, EXPR_ARITH, ARITH_NEGATE, WORD, _EXPR_ARITH);

    testTokenization("$((~a))", DOLLAR, EXPR_ARITH, ARITH_BITWISE_NEGATE, WORD, _EXPR_ARITH);

    testTokenization("$((a>>2))", DOLLAR, EXPR_ARITH, WORD, ARITH_SHIFT_RIGHT, ARITH_NUMBER, _EXPR_ARITH);

    testTokenization("$((a<<2))", DOLLAR, EXPR_ARITH, WORD, ARITH_SHIFT_LEFT, ARITH_NUMBER, _EXPR_ARITH);

    testTokenization("$((a|2))", DOLLAR, EXPR_ARITH, WORD, PIPE, ARITH_NUMBER, _EXPR_ARITH);

    testTokenization("$((a&2))", DOLLAR, EXPR_ARITH, WORD, ARITH_BITWISE_AND, ARITH_NUMBER, _EXPR_ARITH);

    testTokenization("$((a^2))", DOLLAR, EXPR_ARITH, WORD, ARITH_BITWISE_XOR, ARITH_NUMBER, _EXPR_ARITH);

    testTokenization("$((a%2))", DOLLAR, EXPR_ARITH, WORD, ARITH_MOD, ARITH_NUMBER, _EXPR_ARITH);

    testTokenization("$((a-2))", DOLLAR, EXPR_ARITH, WORD, ARITH_MINUS, ARITH_NUMBER, _EXPR_ARITH);

    testTokenization("$((a--))", DOLLAR, EXPR_ARITH, WORD, ARITH_MINUS_MINUS, _EXPR_ARITH);

    testTokenization("$((--a))", DOLLAR, EXPR_ARITH, ARITH_MINUS_MINUS, WORD, _EXPR_ARITH);

    testTokenization("$((a,2))", DOLLAR, EXPR_ARITH, WORD, COMMA, ARITH_NUMBER, _EXPR_ARITH);

    testTokenization("$((a>2))", DOLLAR, EXPR_ARITH, WORD, ARITH_GT, ARITH_NUMBER, _EXPR_ARITH);

    testTokenization("$((a > 2))", DOLLAR, EXPR_ARITH, WORD, WHITESPACE, ARITH_GT, WHITESPACE, ARITH_NUMBER, _EXPR_ARITH);

    testTokenization("$((a>=2))", DOLLAR, EXPR_ARITH, WORD, ARITH_GE, ARITH_NUMBER, _EXPR_ARITH);

    testTokenization("$((a<2))", DOLLAR, EXPR_ARITH, WORD, ARITH_LT, ARITH_NUMBER, _EXPR_ARITH);

    testTokenization("$((a<=2))", DOLLAR, EXPR_ARITH, WORD, ARITH_LE, ARITH_NUMBER, _EXPR_ARITH);

    testTokenization("$((1+-45))", DOLLAR, EXPR_ARITH, ARITH_NUMBER, ARITH_PLUS, ARITH_MINUS, ARITH_NUMBER, _EXPR_ARITH);

    testTokenization("$((1+(-45)))", DOLLAR, EXPR_ARITH, ARITH_NUMBER, ARITH_PLUS, LEFT_PAREN, ARITH_MINUS, ARITH_NUMBER, RIGHT_PAREN, _EXPR_ARITH);

    testTokenization("$((1+---45))", DOLLAR, EXPR_ARITH, ARITH_NUMBER, ARITH_PLUS, ARITH_MINUS, ARITH_MINUS, ARITH_MINUS, ARITH_NUMBER, _EXPR_ARITH);

    testTokenization("$(((1 << 10)))", DOLLAR, LEFT_PAREN, EXPR_ARITH, ARITH_NUMBER, WHITESPACE, ARITH_SHIFT_LEFT, WHITESPACE, ARITH_NUMBER, _EXPR_ARITH, RIGHT_PAREN);

    testTokenization("$((1 < \"1\"))", DOLLAR, EXPR_ARITH, ARITH_NUMBER, WHITESPACE, ARITH_LT, WHITESPACE, STRING_BEGIN, STRING_CONTENT, STRING_END, _EXPR_ARITH);

    testTokenization("$((((1))))", DOLLAR, LEFT_PAREN, EXPR_ARITH, LEFT_PAREN, ARITH_NUMBER, RIGHT_PAREN, _EXPR_ARITH, RIGHT_PAREN);

    testTokenization("$((10#$x/10))", DOLLAR, EXPR_ARITH, ARITH_NUMBER, ARITH_BASE_CHAR, VARIABLE, ARITH_DIV, ARITH_NUMBER, _EXPR_ARITH);
  }

  @Ignore
  @Test
  public void testLetExpressions() {
    //fixme unsure how the let expression should be tokenized. A solution might be to parse it as an lazy expression
    testTokenization("let a+=1", LET_KEYWORD, WHITESPACE, ASSIGNMENT_WORD, ARITH_ASS_PLUS, ARITH_NUMBER);
    testTokenization("let", LET_KEYWORD);
  }

  @Test
  public void testShebang() {
    testTokenization("#!", SHEBANG);

    testTokenization("#!/bin/bash", SHEBANG);

    testTokenization("#!/bin/bash\n", SHEBANG);

    testTokenization("#!/bin/bash\n\r", SHEBANG, LINE_FEED);

    testTokenization("\n#!/bin/bash", LINE_FEED, SHEBANG);
  }

  @Test
  public void testWhitespace() {
    testTokenization(" ", WHITESPACE);

    testTokenization("\t", WHITESPACE);

    testTokenization("\f", WHITESPACE);

    testTokenization(" \\\n ", WHITESPACE, LINE_CONTINUATION, WHITESPACE);
  }

  @Test
  public void testIdentifier() {
    testTokenization("a", WORD);

    testTokenization("ab", WORD);

    testTokenization("abc123", WORD);

    testTokenization("ABC_123", WORD);
  }

  @Test
  public void testStrings() {
    testTokenization("\"\"", STRING_BEGIN, STRING_END);
    testTokenization("\"abc\"", STRING_BEGIN, STRING_CONTENT, STRING_END);
    testTokenization("\"abc\"\"abc\"", STRING_BEGIN, STRING_CONTENT, STRING_END, STRING_BEGIN, STRING_CONTENT, STRING_END);
    testTokenization("\"\\.\"", STRING_BEGIN, STRING_CONTENT, STRING_END);
    testTokenization("\"\\n\"", STRING_BEGIN, STRING_CONTENT, STRING_END);
    testTokenization("\" \"", STRING_BEGIN, STRING_CONTENT, STRING_END);
    testTokenization("\"$( a )\"", STRING_BEGIN, DOLLAR, LEFT_PAREN, WHITESPACE, WORD, WHITESPACE, RIGHT_PAREN, STRING_END);
    testTokenization("\"$a\"", STRING_BEGIN, VARIABLE, STRING_END);
    testTokenization("\"a b\\\"\"", STRING_BEGIN, STRING_CONTENT, STRING_END);
    testTokenization("a b \"a b \\\"\" \"a\" b",
        WORD, WHITESPACE, WORD, WHITESPACE, STRING_BEGIN, STRING_CONTENT, STRING_END, WHITESPACE,
        STRING_BEGIN, STRING_CONTENT, STRING_END, WHITESPACE, WORD);
    testTokenization("\"a$\"", STRING_BEGIN, STRING_CONTENT, STRING_END);

    testTokenization("\"$(\"hey there\")\"", STRING_BEGIN, DOLLAR, LEFT_PAREN, STRING_BEGIN, STRING_CONTENT, STRING_END, RIGHT_PAREN, STRING_END);
    testTokenization("\"$(echo \\\"\\\")\"", STRING_BEGIN, DOLLAR, LEFT_PAREN, WORD, WHITESPACE, WORD, RIGHT_PAREN, STRING_END);
    testTokenization("\"$(echo \\\"\\\")\" a", STRING_BEGIN, DOLLAR, LEFT_PAREN, WORD, WHITESPACE, WORD, RIGHT_PAREN, STRING_END, WHITESPACE, WORD);

    testTokenization("\"$(echo || echo)\"", STRING_BEGIN, DOLLAR, LEFT_PAREN, WORD, WHITESPACE, OR_OR, WHITESPACE, WORD, RIGHT_PAREN, STRING_END);
    testTokenization("\"$(echo && echo)\"", STRING_BEGIN, DOLLAR, LEFT_PAREN, WORD, WHITESPACE, AND_AND, WHITESPACE, WORD, RIGHT_PAREN, STRING_END);
    testTokenization("\"$(abc)\"", STRING_BEGIN, DOLLAR, LEFT_PAREN, WORD, RIGHT_PAREN, STRING_END);
    testTokenization("\"$(1)\"", STRING_BEGIN, DOLLAR, LEFT_PAREN, INTEGER_LITERAL, RIGHT_PAREN, STRING_END);
    testTokenization("\"$((1))\"", STRING_BEGIN, DOLLAR, EXPR_ARITH, ARITH_NUMBER, _EXPR_ARITH, STRING_END);

    // "$("s/(/")" , the subshell command should be parsed as a word
    testTokenization("\"$(\"s/(/\")\"", STRING_BEGIN, DOLLAR, LEFT_PAREN, STRING_BEGIN, STRING_CONTENT, STRING_END, RIGHT_PAREN, STRING_END);

    testTokenization("\\.", WORD);
    testTokenization("\\n", WORD);
    testTokenization("\\>", WORD);
    testTokenization("\\<", WORD);

    testTokenization("\"||\"", STRING_BEGIN, STRING_CONTENT, STRING_END);
    testTokenization("\"$(||)\"", STRING_BEGIN, DOLLAR, LEFT_PAREN, OR_OR, RIGHT_PAREN, STRING_END);

    testTokenization("\"&&\"", STRING_BEGIN, STRING_CONTENT, STRING_END);
    testTokenization("\"$(&&)\"", STRING_BEGIN, DOLLAR, LEFT_PAREN, AND_AND, RIGHT_PAREN, STRING_END);

    testTokenization("a#%%", WORD);

    testTokenization("a#%%[0-9]", WORD);

    testTokenization("echo level%%[a-zA-Z]*", WORD, WHITESPACE, WORD);
    testTokenization("[ \\${a} ]", EXPR_CONDITIONAL, WORD, LEFT_CURLY, WORD, RIGHT_CURLY, _EXPR_CONDITIONAL);
    testTokenization("[  ]", EXPR_CONDITIONAL, _EXPR_CONDITIONAL);
    testTokenization("[ ]", EXPR_CONDITIONAL, _EXPR_CONDITIONAL);
    testTokenization("[ a  ]", EXPR_CONDITIONAL, WORD, WHITESPACE, _EXPR_CONDITIONAL);
    testTokenization("[ a | b ]", EXPR_CONDITIONAL, WORD, WHITESPACE, PIPE, WHITESPACE, WORD, _EXPR_CONDITIONAL);
    testTokenization("[[ a || b ]]", BRACKET_KEYWORD, WORD, WHITESPACE, OR_OR, WHITESPACE, WORD, _BRACKET_KEYWORD);
    testTokenization("${rdev%:*?}", DOLLAR, LEFT_CURLY, WORD, PARAM_EXPANSION_OP_PERCENT, PARAM_EXPANSION_OP_COLON, PARAM_EXPANSION_OP_STAR, PARAM_EXPANSION_OP_QMARK, RIGHT_CURLY);
    testTokenization("${@!-+}", DOLLAR, LEFT_CURLY, PARAM_EXPANSION_OP_AT, PARAM_EXPANSION_OP_EXCL, PARAM_EXPANSION_OP_MINUS, PARAM_EXPANSION_OP_PLUS, RIGHT_CURLY);
    testTokenization("${a[@]}", DOLLAR, LEFT_CURLY, WORD, LEFT_SQUARE, PARAM_EXPANSION_OP_AT, RIGHT_SQUARE, RIGHT_CURLY);
    testTokenization("${\\ }", DOLLAR, LEFT_CURLY, WORD, RIGHT_CURLY);

    testTokenization("$\"\"", STRING_BEGIN, STRING_END);
    testTokenization("$\"abc\"", STRING_BEGIN, STRING_CONTENT, STRING_END);

    testTokenization("$''", STRING2);
    testTokenization("$'abc'", STRING2);

    testTokenization("\"(( $1 ))\"", STRING_BEGIN, STRING_CONTENT, VARIABLE, STRING_CONTENT, STRING_END);

    //multiline strings
    testTokenization("\"a\nb\nc\"", STRING_BEGIN, STRING_CONTENT, STRING_END);
    testTokenization("\"\n\"", STRING_BEGIN, STRING_CONTENT, STRING_END);
    //multiline string2
    testTokenization("'a\nb\nc'", STRING2);
    testTokenization("'\n'", STRING2);

    //test escaped chars
    testTokenization("\\*", WORD);
    testTokenization("\\ ", WORD);
    testTokenization("\\{", WORD);
    testTokenization("\\;", WORD);
    testTokenization("\\.", WORD);
    testTokenization("\\r", WORD);
    testTokenization("\\n", WORD);
    testTokenization("\\:", WORD);
    testTokenization("\\(", WORD);
    testTokenization("\\)", WORD);
    testTokenization("\\\"", WORD);
    testTokenization("\\\\", WORD);
    testTokenization("\\>", WORD);
    testTokenization("\\<", WORD);
    testTokenization("\\$", WORD);
    testTokenization("\\ ", WORD);
    testTokenization("\\?", WORD);
    testTokenization("\\!", WORD);
    //fixme: line continuation, check with spec
    testTokenization("abc\\\nabc", WORD);

    //no escape char here
    //fixme what is right here?
    //testTokenization("'$a\\' 'a'", STRING2, WHITESPACE, STRING2);
  }

  @Test
  public void testSubshellString() {
    testTokenization("\"$( )\"", STRING_BEGIN, DOLLAR, LEFT_PAREN, WHITESPACE, RIGHT_PAREN, STRING_END);

    testTokenization("\"$( () )\"", STRING_BEGIN, DOLLAR, LEFT_PAREN, WHITESPACE, LEFT_PAREN,
        RIGHT_PAREN, WHITESPACE, RIGHT_PAREN, STRING_END);
  }

  @Test
  public void testSubshellSubstring() {
    testTokenization("\"$( \"echo\" )\"", STRING_BEGIN, DOLLAR, LEFT_PAREN, WHITESPACE, STRING_BEGIN, STRING_CONTENT, STRING_END, WHITESPACE, RIGHT_PAREN, STRING_END);

    testTokenization("\"$( ( \"\" \"\" ) )\"", STRING_BEGIN, DOLLAR, LEFT_PAREN, WHITESPACE, LEFT_PAREN, WHITESPACE, STRING_BEGIN, STRING_END, WHITESPACE, STRING_BEGIN, STRING_END, WHITESPACE, RIGHT_PAREN, WHITESPACE, RIGHT_PAREN, STRING_END);

    testTokenization("\"$( ( \"\" ))\"", STRING_BEGIN, DOLLAR, LEFT_PAREN, WHITESPACE, LEFT_PAREN, WHITESPACE, STRING_BEGIN, STRING_END, WHITESPACE, RIGHT_PAREN, RIGHT_PAREN, STRING_END);

    testTokenization("\"$( \"$( echo )\" )\"", STRING_BEGIN, DOLLAR, LEFT_PAREN, WHITESPACE, STRING_BEGIN, DOLLAR, LEFT_PAREN, WHITESPACE, WORD, WHITESPACE, RIGHT_PAREN, STRING_END, WHITESPACE, RIGHT_PAREN, STRING_END);

    testTokenization("\"$(\"$(\"abcd\")\")\"", STRING_BEGIN, DOLLAR, LEFT_PAREN, STRING_BEGIN, DOLLAR, LEFT_PAREN, STRING_BEGIN, STRING_CONTENT, STRING_END, RIGHT_PAREN, STRING_END, RIGHT_PAREN, STRING_END);
  }

  @Test
  public void testWords() {
    testTokenization("%", WORD);
    testTokenization("a%b", WORD);
    testTokenization("a%b}", WORD, RIGHT_CURLY);
    testTokenization("echo level%%[a-zA-Z]*", WORD, WHITESPACE, WORD);
    testTokenization("tr [:upper:]", WORD, WHITESPACE, WORD);
    testTokenization("[!\"$2\"]", WORD, STRING_BEGIN, VARIABLE, STRING_END, WORD);

    testTokenization("unset todo_list[$todo_id]", WORD, WHITESPACE, ASSIGNMENT_WORD, LEFT_SQUARE, VARIABLE, RIGHT_SQUARE);
  }

  @Test
  public void testInternalCommands() {
    testTokenization("declare", WORD);
    testTokenization("echo", WORD);
    testTokenization("export", WORD);
    testTokenization("readonly", WORD);
    testTokenization("local", WORD);

    testTokenization(".", WORD);
    testTokenization(". /home/user/bashrc", WORD, WHITESPACE, WORD);
    testTokenization(". /home/user/bashrc$a", WORD, WHITESPACE, WORD, VARIABLE);
    testTokenization(". x >& x", WORD, WHITESPACE, WORD, WHITESPACE, REDIRECT_GREATER_AMP, WHITESPACE, WORD);

    testTokenization("''", STRING2);
    testTokenization("\"$('')\"", STRING_BEGIN, DOLLAR, LEFT_PAREN, STRING2, RIGHT_PAREN, STRING_END);
    testTokenization("\"$('(')\"", STRING_BEGIN, DOLLAR, LEFT_PAREN, STRING2, RIGHT_PAREN, STRING_END);

    testTokenization("\"$'(')\"", STRING_BEGIN, STRING_CONTENT, STRING_END);

    testTokenization("echo $", WORD, WHITESPACE, DOLLAR);
  }

  @Test
  public void testExpressions() {
    testTokenization("if [ -n \"a\" ]; then a; fi;",
        IF_KEYWORD, WHITESPACE, EXPR_CONDITIONAL, COND_OP, WHITESPACE, STRING_BEGIN, STRING_CONTENT, STRING_END, _EXPR_CONDITIONAL, SEMI,
        WHITESPACE, THEN_KEYWORD, WHITESPACE, WORD, SEMI, WHITESPACE, FI_KEYWORD, SEMI);

    testTokenization("$((1+2))", DOLLAR, EXPR_ARITH, ARITH_NUMBER, ARITH_PLUS, ARITH_NUMBER, _EXPR_ARITH);
    testTokenization("((i=$(echo 1)))", EXPR_ARITH, ASSIGNMENT_WORD, EQ, DOLLAR, LEFT_PAREN, WORD, WHITESPACE, INTEGER_LITERAL, RIGHT_PAREN, _EXPR_ARITH);
    testTokenization("((i=$((1 + 9))))",
        EXPR_ARITH, ASSIGNMENT_WORD, EQ, DOLLAR, EXPR_ARITH, ARITH_NUMBER, WHITESPACE,
        ARITH_PLUS, WHITESPACE, ARITH_NUMBER, _EXPR_ARITH, _EXPR_ARITH);

    testTokenization("((1 == 1 ? 0 : 0))",
        EXPR_ARITH, ARITH_NUMBER, WHITESPACE, ARITH_EQ, WHITESPACE, ARITH_NUMBER, WHITESPACE, ARITH_QMARK,
        WHITESPACE, ARITH_NUMBER, WHITESPACE, ARITH_COLON, WHITESPACE, ARITH_NUMBER, _EXPR_ARITH);

    testTokenization("a=(\na #this is a comment\nb)", ASSIGNMENT_WORD, EQ, LEFT_PAREN, LINE_FEED, WORD, WHITESPACE, COMMENT, LINE_FEED, WORD, RIGHT_PAREN);
  }

  @Test
  public void testSubshell() {
    testTokenization("$(echo \"$1\")",
        DOLLAR, LEFT_PAREN, WORD, WHITESPACE, STRING_BEGIN, VARIABLE, STRING_END, RIGHT_PAREN);
    testTokenization("$(($(echo \"$1\")))",
        DOLLAR, EXPR_ARITH, DOLLAR, LEFT_PAREN, WORD, WHITESPACE, STRING_BEGIN, VARIABLE, STRING_END, RIGHT_PAREN, _EXPR_ARITH);
    testTokenization("`for d in`",
        BACKQUOTE, FOR_KEYWORD, WHITESPACE, WORD, WHITESPACE, WORD, BACKQUOTE);
    testTokenization("[ \"`dd`\" ]",
        EXPR_CONDITIONAL, STRING_BEGIN, BACKQUOTE, WORD, BACKQUOTE, STRING_END, _EXPR_CONDITIONAL);
  }

  @Test
  public void testNumber() {
    testTokenization("123", INTEGER_LITERAL);
    testTokenization("$((123))", DOLLAR, EXPR_ARITH, ARITH_NUMBER, _EXPR_ARITH);

    testTokenization("123 456", INTEGER_LITERAL, WHITESPACE, INTEGER_LITERAL);
    testTokenization("$((123 234))", DOLLAR, EXPR_ARITH, ARITH_NUMBER, WHITESPACE, ARITH_NUMBER, _EXPR_ARITH);
  }

  @Test
  public void testFunction() {
    testTokenization("function", FUNCTION_KEYWORD);
  }

  @Test
  public void testVariable() {
    testTokenization("$a", VARIABLE);
    testTokenization("$abc", VARIABLE);
    testTokenization("$abc123_", VARIABLE);
    testTokenization("$$", VARIABLE);
    testTokenization("$1", VARIABLE);
    testTokenization("$*", VARIABLE);
    testTokenization("}", RIGHT_CURLY);
    testTokenization("${1}", DOLLAR, LEFT_CURLY, WORD, RIGHT_CURLY);
    testTokenization("${a}", DOLLAR, LEFT_CURLY, WORD, RIGHT_CURLY);
    testTokenization("${a%}", DOLLAR, LEFT_CURLY, WORD, PARAM_EXPANSION_OP_PERCENT, RIGHT_CURLY);
    testTokenization("${a%b}", DOLLAR, LEFT_CURLY, WORD, PARAM_EXPANSION_OP_PERCENT, WORD, RIGHT_CURLY);
    testTokenization("${#a}", DOLLAR, LEFT_CURLY, PARAM_EXPANSION_OP_HASH, WORD, RIGHT_CURLY);
    testTokenization("${a1}", DOLLAR, LEFT_CURLY, WORD, RIGHT_CURLY);
    //bad substitution, but the lexer must match
    testTokenization("${/}", DOLLAR, LEFT_CURLY, PARAM_EXPANSION_OP_SLASH, RIGHT_CURLY);

    testTokenization("${!imageformat_*}", DOLLAR, LEFT_CURLY, PARAM_EXPANSION_OP_EXCL, WORD, PARAM_EXPANSION_OP_STAR, RIGHT_CURLY);
  }

  @Test
  public void testRedirect1() {
    testTokenization(">&2", GREATER_THAN, FILEDESCRIPTOR);
    testTokenization("<&1", LESS_THAN, FILEDESCRIPTOR);
    testTokenization("<<", HEREDOC_MARKER_TAG);
    testTokenization("<<<", REDIRECT_HERE_STRING);
    testTokenization("<<-", HEREDOC_MARKER_TAG);
    testTokenization("<>", REDIRECT_LESS_GREATER);
    testTokenization(">|", REDIRECT_GREATER_BAR);
    testTokenization(">1", GREATER_THAN, INTEGER_LITERAL);
    testTokenization("> 1", GREATER_THAN, WHITESPACE, INTEGER_LITERAL);
    testTokenization(">&1", GREATER_THAN, FILEDESCRIPTOR);

    testTokenization("<&-", LESS_THAN, FILEDESCRIPTOR);
    testTokenization(">&-", GREATER_THAN, FILEDESCRIPTOR);
    testTokenization("1>&-", INTEGER_LITERAL, GREATER_THAN, FILEDESCRIPTOR);
    testTokenization("1>&-", INTEGER_LITERAL, GREATER_THAN, FILEDESCRIPTOR);

    testTokenization("3>&9", INTEGER_LITERAL, GREATER_THAN, FILEDESCRIPTOR);
    testTokenization("3>&10", INTEGER_LITERAL, GREATER_THAN, FILEDESCRIPTOR);
    testTokenization("10>&10", INTEGER_LITERAL, GREATER_THAN, FILEDESCRIPTOR);
    testTokenization("10>&a123", INTEGER_LITERAL, REDIRECT_GREATER_AMP, WORD);
    testTokenization("10>& a123", INTEGER_LITERAL, REDIRECT_GREATER_AMP, WHITESPACE, WORD);

    testTokenization("$(a >&1)", DOLLAR, LEFT_PAREN, WORD, WHITESPACE, GREATER_THAN, FILEDESCRIPTOR, RIGHT_PAREN);
  }

  @Test
  public void testConditional() {
    testTokenization("[ 1 = \"$backgrounded\" ]", EXPR_CONDITIONAL, WORD, WHITESPACE, COND_OP, WHITESPACE, STRING_BEGIN, VARIABLE, STRING_END, _EXPR_CONDITIONAL);

    testTokenization("[ 1 == 1 ]", EXPR_CONDITIONAL, WORD, WHITESPACE, COND_OP_EQ_EQ, WHITESPACE, WORD, _EXPR_CONDITIONAL);

    testTokenization("[ 1 =~ 1 ]", EXPR_CONDITIONAL, WORD, WHITESPACE, COND_OP_REGEX, WHITESPACE, WORD, _EXPR_CONDITIONAL);
  }

  @Test
  public void testBracket() {
    testTokenization("[[ -f test.txt ]]", BRACKET_KEYWORD, COND_OP, WHITESPACE, WORD, _BRACKET_KEYWORD);
    testTokenization(" ]]", WHITESPACE, WORD);
    testTokenization("  ]]", WHITESPACE, WHITESPACE, WORD);
    testTokenization("[[  -f test.txt   ]]", BRACKET_KEYWORD, WHITESPACE, COND_OP, WHITESPACE, WORD, WHITESPACE, WHITESPACE, _BRACKET_KEYWORD);
    testTokenization("[[ !(a) ]]", BRACKET_KEYWORD, COND_OP_NOT, LEFT_PAREN, WORD, RIGHT_PAREN, _BRACKET_KEYWORD);

    testTokenization("[[ a && b ]]", BRACKET_KEYWORD, WORD, WHITESPACE, AND_AND, WHITESPACE, WORD, _BRACKET_KEYWORD);
    testTokenization("[[ a || b ]]", BRACKET_KEYWORD, WORD, WHITESPACE, OR_OR, WHITESPACE, WORD, _BRACKET_KEYWORD);

    testTokenization("[[ -z \"\" ]]", BRACKET_KEYWORD, COND_OP, WHITESPACE, STRING_BEGIN, STRING_END, _BRACKET_KEYWORD);

    testTokenization("[[ a == b ]]", BRACKET_KEYWORD, WORD, WHITESPACE, COND_OP_EQ_EQ, WHITESPACE, WORD, _BRACKET_KEYWORD);
    testTokenization("[[ a =~ b ]]", BRACKET_KEYWORD, WORD, WHITESPACE, COND_OP_REGEX, WHITESPACE, WORD, _BRACKET_KEYWORD);
  }

  @Test
  public void testParameterSubstitution() {
    testTokenization("${a=x}", DOLLAR, LEFT_CURLY, WORD, PARAM_EXPANSION_OP_EQ, WORD, RIGHT_CURLY);
    testTokenization("${a:=x}", DOLLAR, LEFT_CURLY, WORD, PARAM_EXPANSION_OP_COLON_EQ, WORD, RIGHT_CURLY);

    testTokenization("${a-x}", DOLLAR, LEFT_CURLY, WORD, PARAM_EXPANSION_OP_MINUS, WORD, RIGHT_CURLY);
    testTokenization("${a:-x}", DOLLAR, LEFT_CURLY, WORD, PARAM_EXPANSION_OP_COLON_MINUS, WORD, RIGHT_CURLY);

    testTokenization("${a:?x}", DOLLAR, LEFT_CURLY, WORD, PARAM_EXPANSION_OP_COLON_QMARK, WORD, RIGHT_CURLY);

    testTokenization("${a+x}", DOLLAR, LEFT_CURLY, WORD, PARAM_EXPANSION_OP_PLUS, WORD, RIGHT_CURLY);
    testTokenization("${a:+x}", DOLLAR, LEFT_CURLY, WORD, PARAM_EXPANSION_OP_COLON_PLUS, WORD, RIGHT_CURLY);

    testTokenization("${!a}", DOLLAR, LEFT_CURLY, PARAM_EXPANSION_OP_EXCL, WORD, RIGHT_CURLY);

    testTokenization("${a?x}", DOLLAR, LEFT_CURLY, WORD, PARAM_EXPANSION_OP_QMARK, WORD, RIGHT_CURLY);

    testTokenization("${a-(none)}", DOLLAR, LEFT_CURLY, WORD, PARAM_EXPANSION_OP_MINUS, LEFT_PAREN, WORD, RIGHT_PAREN, RIGHT_CURLY);

    testTokenization("${@}", DOLLAR, LEFT_CURLY, PARAM_EXPANSION_OP_AT, RIGHT_CURLY);

    //either an empty replacement or a single // token, the 2nd is easier for our lexer
    //bash seems to parse this in the same way
    testTokenization("${x//}", DOLLAR, LEFT_CURLY, WORD, PARAM_EXPANSION_OP_SLASH_SLASH, RIGHT_CURLY);

    // // followed by an empty repalcement
    testTokenization("${x///}", DOLLAR, LEFT_CURLY, WORD, PARAM_EXPANSION_OP_SLASH_SLASH, PARAM_EXPANSION_OP_SLASH, RIGHT_CURLY);

    testTokenization("${x/a/}", DOLLAR, LEFT_CURLY, WORD, PARAM_EXPANSION_OP_SLASH, PARAM_EXPANSION_PATTERN, PARAM_EXPANSION_OP_SLASH, RIGHT_CURLY);

    testTokenization("${x/a//}", DOLLAR, LEFT_CURLY, WORD, PARAM_EXPANSION_OP_SLASH, PARAM_EXPANSION_PATTERN, PARAM_EXPANSION_OP_SLASH, WORD, RIGHT_CURLY);

    testTokenization("${x/,//}", DOLLAR, LEFT_CURLY, WORD, PARAM_EXPANSION_OP_SLASH, PARAM_EXPANSION_PATTERN, PARAM_EXPANSION_OP_SLASH, WORD, RIGHT_CURLY);

    testTokenization("${x/a}", DOLLAR, LEFT_CURLY, WORD, PARAM_EXPANSION_OP_SLASH, PARAM_EXPANSION_PATTERN, RIGHT_CURLY);

    //replace newline with space
    testTokenization("${x/\n/ }", DOLLAR, LEFT_CURLY, WORD, PARAM_EXPANSION_OP_SLASH, PARAM_EXPANSION_PATTERN, PARAM_EXPANSION_OP_SLASH, WORD, RIGHT_CURLY);

    testTokenization("${input//[[:digit:].]/}", DOLLAR, LEFT_CURLY, WORD, PARAM_EXPANSION_OP_SLASH_SLASH, PARAM_EXPANSION_PATTERN, PARAM_EXPANSION_OP_SLASH, RIGHT_CURLY);

    testTokenization("${1/} X", DOLLAR, LEFT_CURLY, WORD, PARAM_EXPANSION_OP_SLASH, RIGHT_CURLY, WHITESPACE, WORD);
    testTokenization("function x\n" +
            "${1/}\n" +
            "${1/}\n" +
            "}",
        FUNCTION_KEYWORD, WHITESPACE, WORD, LINE_FEED, DOLLAR, LEFT_CURLY, WORD, PARAM_EXPANSION_OP_SLASH, RIGHT_CURLY, LINE_FEED,
        DOLLAR, LEFT_CURLY, WORD, PARAM_EXPANSION_OP_SLASH, RIGHT_CURLY, LINE_FEED,
        RIGHT_CURLY);
  }

  @Test
  public void testWeirdStuff1() {
    testTokenization(": > a", WORD, WHITESPACE, GREATER_THAN, WHITESPACE, WORD);
    testTokenization("^", WORD);
    testTokenization("$!", VARIABLE);
    testTokenization("!", BANG_TOKEN);
    testTokenization("+m", WORD);
    testTokenization("\\#", WORD);
    testTokenization("a,b", WORD);
    testTokenization("~", WORD);
    testTokenization("a~", WORD);
    testTokenization("\\$", WORD);
    testTokenization("a[1]=2", ASSIGNMENT_WORD, LEFT_SQUARE, ARITH_NUMBER, RIGHT_SQUARE, EQ, INTEGER_LITERAL);
    testTokenization("a[1]='2'", ASSIGNMENT_WORD, LEFT_SQUARE, ARITH_NUMBER, RIGHT_SQUARE, EQ, STRING2);
    testTokenization("a[1+2]='2'", ASSIGNMENT_WORD, LEFT_SQUARE, ARITH_NUMBER, ARITH_PLUS, ARITH_NUMBER, RIGHT_SQUARE, EQ, STRING2);
    testTokenization("esac;", WORD, SEMI);

    //"$(echo "123")"
    testTokenization("\"$(echo 123)\"", STRING_BEGIN, DOLLAR, LEFT_PAREN, WORD, WHITESPACE, INTEGER_LITERAL, RIGHT_PAREN, STRING_END);
    testTokenization("\"$(\"123\")\"", STRING_BEGIN, DOLLAR, LEFT_PAREN, STRING_BEGIN, STRING_CONTENT, STRING_END, RIGHT_PAREN, STRING_END);
    //testTokenization("\"$( \"a\")\"", BashElementTypes.STRING_ELEMENT);
    //testTokenization("\"$( \"123\")\"", BashElementTypes.STRING_ELEMENT);
    //testTokenization("\"$(echo \"123\")\"", BashElementTypes.STRING_ELEMENT);
    //testTokenization("\"$(echo echo \"123\")\"", BashElementTypes.STRING_ELEMENT);
    //testTokenization("\"$(echo \"$(echo \"123\")\")\"", BashElementTypes.STRING_ELEMENT);

    //tilde expansion
    testTokenization("~", WORD);
    testTokenization("~user", WORD);
    testTokenization("~+", WORD);
    testTokenization("~-", WORD);
    testTokenization("~1", WORD);
    testTokenization("~+1", WORD);
    testTokenization("~-1", WORD);

    //weird expansions
    testTokenization("echo ${feld[${index}]}", WORD, WHITESPACE, DOLLAR, LEFT_CURLY,
        WORD, LEFT_SQUARE, DOLLAR, LEFT_CURLY, WORD, RIGHT_CURLY, RIGHT_SQUARE, RIGHT_CURLY);

    //shebang line after first line
    testTokenization("echo; #!/bin/sh", WORD, SEMI, WHITESPACE, SHEBANG);

    testTokenization("TOMCAT_HOST_LIST[$index]=$LINE", ASSIGNMENT_WORD, LEFT_SQUARE, VARIABLE, RIGHT_SQUARE, EQ, VARIABLE);

  }


  @Ignore
  @Test
  public void testUnsupported() {

    //keyword as for loop variable
    //fixme currently unsupported, the case lexing is not context sensitive (hard to fix)
    testTokenization("for case in a; do\n" +
        "echo\n" +
        "done;", FOR_KEYWORD, WHITESPACE, CASE_KEYWORD, WHITESPACE, WORD, WHITESPACE, WORD, SEMI, WHITESPACE, DO_KEYWORD, LINE_FEED, WORD, LINE_FEED, DONE_KEYWORD, SEMI);
  }

  @Test
  public void testCaseWhitespacePattern() {
    testTokenization("case x in\n" +
        "a\\ b)\n" +
        ";;\n" +
        "esac", CASE_KEYWORD, WHITESPACE, WORD, WHITESPACE, WORD, LINE_FEED, WORD, RIGHT_PAREN, LINE_FEED, CASE_END, LINE_FEED, ESAC_KEYWORD);

  }

  @Test
  public void testNestedCase() {
    testTokenization("case x in x) ;; esac",
        CASE_KEYWORD, WHITESPACE, WORD, WHITESPACE, WORD, WHITESPACE, WORD, RIGHT_PAREN, WHITESPACE, CASE_END, WHITESPACE, ESAC_KEYWORD);

    testTokenization("$(case x in x) ;; esac)",
        DOLLAR, LEFT_PAREN, CASE_KEYWORD, WHITESPACE, WORD, WHITESPACE, WORD, WHITESPACE, WORD, RIGHT_PAREN, WHITESPACE, CASE_END, WHITESPACE, ESAC_KEYWORD, RIGHT_PAREN);
    testTokenization("(case x in x) ;; esac)",
        LEFT_PAREN, CASE_KEYWORD, WHITESPACE, WORD, WHITESPACE, WORD, WHITESPACE, WORD, RIGHT_PAREN, WHITESPACE, CASE_END, WHITESPACE, ESAC_KEYWORD, RIGHT_PAREN);

    testTokenization("`case x in x) ;; esac `",
        BACKQUOTE, CASE_KEYWORD, WHITESPACE, WORD, WHITESPACE, WORD, WHITESPACE, WORD, RIGHT_PAREN, WHITESPACE, CASE_END, WHITESPACE, ESAC_KEYWORD, WHITESPACE, BACKQUOTE);

    testTokenization("case x in esac",
        CASE_KEYWORD, WHITESPACE, WORD, WHITESPACE, WORD, WHITESPACE, ESAC_KEYWORD);

    testTokenization("`case x in esac`;",
        BACKQUOTE, CASE_KEYWORD, WHITESPACE, WORD, WHITESPACE, WORD, WHITESPACE, ESAC_KEYWORD, BACKQUOTE, SEMI);

    testTokenization("case x in\n" +
            "a\\ b)\n" +
            "x=`case x in x) echo;; esac`\n" +
            ";;\n" +
            "esac",
        CASE_KEYWORD, WHITESPACE, WORD, WHITESPACE, WORD, LINE_FEED,
        WORD, RIGHT_PAREN, LINE_FEED,
        ASSIGNMENT_WORD, EQ, BACKQUOTE, CASE_KEYWORD, WHITESPACE, WORD, WHITESPACE, WORD, WHITESPACE, WORD, RIGHT_PAREN, WHITESPACE, WORD, CASE_END, WHITESPACE, ESAC_KEYWORD, BACKQUOTE, LINE_FEED,
        CASE_END, LINE_FEED, ESAC_KEYWORD);

  }

  @Test
  public void testBackquote1() {
    testTokenization("`", BACKQUOTE);
    testTokenization("``", BACKQUOTE, BACKQUOTE);
    testTokenization("`echo a`", BACKQUOTE, WORD, WHITESPACE, WORD, BACKQUOTE);
    testTokenization("`\\``", BACKQUOTE, WORD, BACKQUOTE);
  }

  @Test
  public void testCasePattern() {
    testTokenization("case a in a=a);; esac",
        CASE_KEYWORD, WHITESPACE, WORD, WHITESPACE, WORD, WHITESPACE, WORD, RIGHT_PAREN,
        CASE_END, WHITESPACE, ESAC_KEYWORD);

    testTokenization("case a in a/ui);; esac",
        CASE_KEYWORD, WHITESPACE, WORD, WHITESPACE, WORD, WHITESPACE, WORD, RIGHT_PAREN,
        CASE_END, WHITESPACE, ESAC_KEYWORD);

    testTokenization("case a in a#);; esac",
        CASE_KEYWORD, WHITESPACE, WORD, WHITESPACE, WORD, WHITESPACE, WORD, RIGHT_PAREN,
        CASE_END, WHITESPACE, ESAC_KEYWORD);

    testTokenization("case a in\n  a#);; esac",
        CASE_KEYWORD, WHITESPACE, WORD, WHITESPACE, WORD,
        LINE_FEED, WHITESPACE, WHITESPACE,
        WORD, RIGHT_PAREN, CASE_END, WHITESPACE, ESAC_KEYWORD);

    testTokenization("case a in \"a b\") echo a;; esac",
        CASE_KEYWORD, WHITESPACE, WORD, WHITESPACE, WORD, WHITESPACE, STRING_BEGIN, STRING_CONTENT, STRING_END, RIGHT_PAREN,
        WHITESPACE, WORD, WHITESPACE, WORD, CASE_END, WHITESPACE, ESAC_KEYWORD);

    //v3 vs. v4 changes in end marker
    testTokenization(BashVersion.V4, "case a in a);;& esac",
        CASE_KEYWORD, WHITESPACE, WORD, WHITESPACE, WORD,
        WHITESPACE, WORD, RIGHT_PAREN, CASE_END, WHITESPACE, ESAC_KEYWORD);
    testTokenization(BashVersion.V3, "case a in a);;& esac",
        CASE_KEYWORD, WHITESPACE, WORD, WHITESPACE, WORD,
        WHITESPACE, WORD, RIGHT_PAREN, CASE_END, AMP, WHITESPACE, ESAC_KEYWORD);

    //v3 vs. v4 changes in new end marker
    testTokenization(BashVersion.V4, "case a in a);& esac",
        CASE_KEYWORD, WHITESPACE, WORD, WHITESPACE, WORD,
        WHITESPACE, WORD, RIGHT_PAREN, CASE_END, WHITESPACE, ESAC_KEYWORD);
    testTokenization(BashVersion.V3, "case a in a);& esac",
        CASE_KEYWORD, WHITESPACE, WORD, WHITESPACE, WORD,
        WHITESPACE, WORD, RIGHT_PAREN, SEMI, AMP, WHITESPACE, ESAC_KEYWORD);

    testTokenization("case a in a=a) echo a;; esac;",
        CASE_KEYWORD, WHITESPACE, WORD, WHITESPACE, WORD, WHITESPACE, WORD, RIGHT_PAREN,
        WHITESPACE, WORD, WHITESPACE, WORD, CASE_END, WHITESPACE, ESAC_KEYWORD, SEMI);

  }

  @Test
  public void testAssignmentList() {
    testTokenization("a=(1)", ASSIGNMENT_WORD, EQ, LEFT_PAREN, WORD, RIGHT_PAREN);
    testTokenization("a=(a b c)", ASSIGNMENT_WORD, EQ, LEFT_PAREN, WORD, WHITESPACE, WORD, WHITESPACE, WORD, RIGHT_PAREN);
    testTokenization("a=(a,b,c)", ASSIGNMENT_WORD, EQ, LEFT_PAREN, WORD, RIGHT_PAREN);
  }

  @Test
  public void testEval() {
    testTokenization("eval [ \"a\" ]",
        WORD, WHITESPACE, EXPR_CONDITIONAL, STRING_BEGIN, STRING_CONTENT, STRING_END, _EXPR_CONDITIONAL);

    testTokenization("eval \"echo $\"",
        WORD, WHITESPACE, STRING_BEGIN, STRING_CONTENT, STRING_END);

    testTokenization("for f in a; do eval [ \"a\" ]; done",
        FOR_KEYWORD, WHITESPACE, WORD, WHITESPACE, WORD, WHITESPACE, WORD, SEMI,
        WHITESPACE, DO_KEYWORD, WHITESPACE,
        WORD, WHITESPACE, EXPR_CONDITIONAL, STRING_BEGIN, STRING_CONTENT, STRING_END, _EXPR_CONDITIONAL,
        SEMI, WHITESPACE, DONE_KEYWORD);

    testTokenization("case a in a) echo [ \"a\" ];; esac",
        CASE_KEYWORD, WHITESPACE, WORD, WHITESPACE, WORD, WHITESPACE, WORD,
        RIGHT_PAREN, WHITESPACE, WORD, WHITESPACE, EXPR_CONDITIONAL, STRING_BEGIN, STRING_CONTENT, STRING_END, _EXPR_CONDITIONAL, CASE_END, WHITESPACE, ESAC_KEYWORD);
  }

  @Test
  public void testNestedStatements() {
    testTokenization("case a in a) for do done ;; esac",
        CASE_KEYWORD, WHITESPACE, WORD, WHITESPACE, WORD, WHITESPACE, WORD,
        RIGHT_PAREN, WHITESPACE, FOR_KEYWORD, WHITESPACE, DO_KEYWORD, WHITESPACE, DONE_KEYWORD, WHITESPACE, CASE_END, WHITESPACE,
        ESAC_KEYWORD);

    testTokenization("case a in a) in ;; esac",
        CASE_KEYWORD, WHITESPACE, WORD, WHITESPACE, WORD, WHITESPACE, WORD,
        RIGHT_PAREN, WHITESPACE, WORD, WHITESPACE, CASE_END, WHITESPACE,
        ESAC_KEYWORD);

    testTokenization("if; a; then\nb #123\nfi",
        IF_KEYWORD, SEMI, WHITESPACE, WORD, SEMI, WHITESPACE, THEN_KEYWORD, LINE_FEED,
        WORD, WHITESPACE, COMMENT, LINE_FEED, FI_KEYWORD);

    testTokenization("for ((a=1;;))",
        FOR_KEYWORD, WHITESPACE, EXPR_ARITH, ASSIGNMENT_WORD, EQ, ARITH_NUMBER, SEMI,
        SEMI, _EXPR_ARITH);

    testTokenization("for ((a=1;a;))",
        FOR_KEYWORD, WHITESPACE, EXPR_ARITH, ASSIGNMENT_WORD, EQ, ARITH_NUMBER, SEMI,
        WORD, SEMI, _EXPR_ARITH);

    testTokenization("for ((a=1;a<2;a=a+1))",
        FOR_KEYWORD, WHITESPACE, EXPR_ARITH, ASSIGNMENT_WORD, EQ, ARITH_NUMBER, SEMI,
        WORD, ARITH_LT, ARITH_NUMBER, SEMI, ASSIGNMENT_WORD, EQ, WORD, ARITH_PLUS, ARITH_NUMBER, _EXPR_ARITH);

    testTokenization("$(read -p \"a\")",
        DOLLAR, LEFT_PAREN, WORD, WHITESPACE, WORD, WHITESPACE, STRING_BEGIN, STRING_CONTENT,
        STRING_END, RIGHT_PAREN);
  }

  @Test
  public void testV4Lexing() {
    //new &>> redirect token
    testTokenization(BashVersion.V4, "a &>> out", WORD, WHITESPACE, REDIRECT_AMP_GREATER_GREATER, WHITESPACE, WORD);
    testTokenization(BashVersion.V3, "a &>> out", WORD, WHITESPACE, AMP, SHIFT_RIGHT, WHITESPACE, WORD);

    //new &> redirect token
    testTokenization(BashVersion.V4, "a &> out", WORD, WHITESPACE, REDIRECT_AMP_GREATER, WHITESPACE, WORD);
    testTokenization(BashVersion.V3, "a &> out", WORD, WHITESPACE, AMP, GREATER_THAN, WHITESPACE, WORD);

    //new |& redirect token
    testTokenization(BashVersion.V4, "a |& b", WORD, WHITESPACE, PIPE_AMP, WHITESPACE, WORD);
    testTokenization(BashVersion.V4, "\"$(a |& b)\"", STRING_BEGIN, DOLLAR,
        LEFT_PAREN, WORD, WHITESPACE, PIPE_AMP, WHITESPACE, WORD, RIGHT_PAREN, STRING_END);
    testTokenization(BashVersion.V3, "a |& b", WORD, WHITESPACE, PIPE, AMP, WHITESPACE, WORD);
  }

  @Test
  public void testParamExpansionNested() {
    //${a${a}}
    testTokenization("${a${b}}", DOLLAR, LEFT_CURLY, WORD,
        DOLLAR, LEFT_CURLY, WORD, RIGHT_CURLY, RIGHT_CURLY);

    //${a$(a)}
    testTokenization("${a$(b)}", DOLLAR, LEFT_CURLY, WORD,
        DOLLAR, LEFT_PAREN, WORD, RIGHT_PAREN, RIGHT_CURLY);

    //${a$($(a))}
    testTokenization("${a$($(b))}", DOLLAR, LEFT_CURLY, WORD,
        DOLLAR, LEFT_PAREN, DOLLAR, LEFT_PAREN, WORD, RIGHT_PAREN, RIGHT_PAREN, RIGHT_CURLY);

    //${a$(a > b)}
    testTokenization("${a$(a > b)}", DOLLAR, LEFT_CURLY, WORD,
        DOLLAR, LEFT_PAREN, WORD, WHITESPACE, GREATER_THAN, WHITESPACE, WORD, RIGHT_PAREN, RIGHT_CURLY);

    //DIR=${$(a $(b)/..)}
    testTokenization("DIR=${$(a $(b)/..)}", ASSIGNMENT_WORD, EQ, DOLLAR, LEFT_CURLY,
        DOLLAR, LEFT_PAREN, WORD, WHITESPACE, DOLLAR, LEFT_PAREN, WORD, RIGHT_PAREN,
        WORD, RIGHT_PAREN, RIGHT_CURLY);
  }

  @Test
  public void testParamExpansion() {
    testTokenization("${a}", DOLLAR, LEFT_CURLY, WORD, RIGHT_CURLY);
    testTokenization("${a:a}", DOLLAR, LEFT_CURLY, WORD, PARAM_EXPANSION_OP_COLON, WORD, RIGHT_CURLY);
    testTokenization("${a:-a}", DOLLAR, LEFT_CURLY, WORD, PARAM_EXPANSION_OP_COLON_MINUS, WORD, RIGHT_CURLY);
    testTokenization("${a:.*}", DOLLAR, LEFT_CURLY, WORD, PARAM_EXPANSION_OP_COLON, PARAM_EXPANSION_OP_DOT, PARAM_EXPANSION_OP_STAR, RIGHT_CURLY);
    testTokenization("${a[@]}", DOLLAR, LEFT_CURLY, WORD, LEFT_SQUARE, PARAM_EXPANSION_OP_AT, RIGHT_SQUARE, RIGHT_CURLY);
    testTokenization("${a[*]}", DOLLAR, LEFT_CURLY, WORD, LEFT_SQUARE, PARAM_EXPANSION_OP_STAR, RIGHT_SQUARE, RIGHT_CURLY);

    testTokenization("${level%%[a-zA-Z]*}", DOLLAR, LEFT_CURLY, WORD, PARAM_EXPANSION_OP_PERCENT, PARAM_EXPANSION_OP_PERCENT, LEFT_SQUARE, WORD, PARAM_EXPANSION_OP_MINUS, WORD, PARAM_EXPANSION_OP_MINUS, WORD, RIGHT_SQUARE, PARAM_EXPANSION_OP_STAR, RIGHT_CURLY);

    testTokenization("${a[var]}", DOLLAR, LEFT_CURLY, WORD, LEFT_SQUARE, WORD, RIGHT_SQUARE, RIGHT_CURLY);
    testTokenization("${a[var+var2]}", DOLLAR, LEFT_CURLY, WORD, LEFT_SQUARE, WORD, ARITH_PLUS, WORD, RIGHT_SQUARE, RIGHT_CURLY);

    testTokenization("${a[var+var2+1]#[a-z][0]}",
        DOLLAR, LEFT_CURLY, WORD, LEFT_SQUARE, WORD, ARITH_PLUS, WORD, ARITH_PLUS, ARITH_NUMBER, RIGHT_SQUARE,
        PARAM_EXPANSION_OP_HASH, LEFT_SQUARE, WORD, PARAM_EXPANSION_OP_MINUS, WORD, RIGHT_SQUARE,
        LEFT_SQUARE, WORD, RIGHT_SQUARE,
        RIGHT_CURLY);

    testTokenization("${#a[1]}", DOLLAR, LEFT_CURLY, PARAM_EXPANSION_OP_HASH, WORD, LEFT_SQUARE, ARITH_NUMBER, RIGHT_SQUARE, RIGHT_CURLY);

    testTokenization("${a-`$[1]`}", DOLLAR, LEFT_CURLY, WORD, PARAM_EXPANSION_OP_MINUS, BACKQUOTE, DOLLAR, EXPR_ARITH_SQUARE, ARITH_NUMBER, _EXPR_ARITH_SQUARE, BACKQUOTE, RIGHT_CURLY);
  }

  @Test
  public void testArithmeticLiterals() {
    testTokenization("$((123))", DOLLAR, EXPR_ARITH, ARITH_NUMBER, _EXPR_ARITH);

    testTokenization("$((0x123))", DOLLAR, EXPR_ARITH, ARITH_HEX_NUMBER, _EXPR_ARITH);
    testTokenization("$((0xA))", DOLLAR, EXPR_ARITH, ARITH_HEX_NUMBER, _EXPR_ARITH);
    testTokenization("$((0xAf))", DOLLAR, EXPR_ARITH, ARITH_HEX_NUMBER, _EXPR_ARITH);
    testTokenization("$((-0xAf))", DOLLAR, EXPR_ARITH, ARITH_MINUS, ARITH_HEX_NUMBER, _EXPR_ARITH);
    testTokenization("$((--0xAf))", DOLLAR, EXPR_ARITH, ARITH_MINUS, ARITH_MINUS, ARITH_HEX_NUMBER, _EXPR_ARITH);
    testTokenization("$((+0xAf))", DOLLAR, EXPR_ARITH, ARITH_PLUS, ARITH_HEX_NUMBER, _EXPR_ARITH);

    testTokenization("$((10#1))", DOLLAR, EXPR_ARITH, ARITH_NUMBER, ARITH_BASE_CHAR, ARITH_NUMBER, _EXPR_ARITH);
    testTokenization("$((10#100))", DOLLAR, EXPR_ARITH, ARITH_NUMBER, ARITH_BASE_CHAR, ARITH_NUMBER, _EXPR_ARITH);
    testTokenization("$((-10#100))", DOLLAR, EXPR_ARITH, ARITH_MINUS, ARITH_NUMBER, ARITH_BASE_CHAR, ARITH_NUMBER, _EXPR_ARITH);
    testTokenization("$((+10#100))", DOLLAR, EXPR_ARITH, ARITH_PLUS, ARITH_NUMBER, ARITH_BASE_CHAR, ARITH_NUMBER, _EXPR_ARITH);

    testTokenization("$((0123))", DOLLAR, EXPR_ARITH, ARITH_OCTAL_NUMBER, _EXPR_ARITH);
    testTokenization("$((-0123))", DOLLAR, EXPR_ARITH, ARITH_MINUS, ARITH_OCTAL_NUMBER, _EXPR_ARITH);

    //also afe is not valid here we expect it to lex because we check the base in
    //an inspection
    testTokenization("$((10#100afe))", DOLLAR, EXPR_ARITH, ARITH_NUMBER, ARITH_BASE_CHAR, ARITH_NUMBER, WORD, _EXPR_ARITH);

    testTokenization("$((12#D))", DOLLAR, EXPR_ARITH, ARITH_NUMBER, ARITH_BASE_CHAR, WORD, _EXPR_ARITH);

    testTokenization("$((35#abcdefghijkl))", DOLLAR, EXPR_ARITH, ARITH_NUMBER, ARITH_BASE_CHAR, WORD, _EXPR_ARITH);
  }


  @Test
  public void testReadCommand() {
    testTokenization("read \"var:\" v[i]", WORD, WHITESPACE, STRING_BEGIN, STRING_CONTENT, STRING_END, WHITESPACE, ASSIGNMENT_WORD, LEFT_SQUARE, WORD, RIGHT_SQUARE);
  }

  @Test
  public void testUmlaut() {
    testTokenization("echo ä", WORD, WHITESPACE, WORD);
  }

  @Test
  public void testSubshellExpr() {
    testTokenization("`dd if=a`", BACKQUOTE, WORD, WHITESPACE, ASSIGNMENT_WORD, EQ, WORD, BACKQUOTE);
  }

  @Test
  public void testIssue201() {
    testTokenization("((!foo))", EXPR_ARITH, ARITH_NEGATE, WORD, _EXPR_ARITH);
  }

  @Test
  public void testHeredoc() {
    testTokenization("cat <<END\nEND", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, LINE_FEED, HEREDOC_MARKER_END);
    testTokenization("cat <<END;\nEND", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, SEMI, LINE_FEED, HEREDOC_MARKER_END);
    testTokenization("cat <<END&& test\nEND", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, AND_AND, WHITESPACE, WORD, LINE_FEED, HEREDOC_MARKER_END);
    testTokenization("cat <<END && test\nEND", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, WHITESPACE, AND_AND, WHITESPACE, WORD, LINE_FEED, HEREDOC_MARKER_END);
    testTokenization("cat <<END || test\nEND", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, WHITESPACE, OR_OR, WHITESPACE, WORD, LINE_FEED, HEREDOC_MARKER_END);

//        testTokenization("cat <<END&&test\nEND", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, AND_AND, WHITESPACE, WORD, LINE_FEED, HEREDOC_MARKER_END);

    testTokenization("cat <<        END", WORD, WHITESPACE, HEREDOC_MARKER_TAG, WHITESPACE, HEREDOC_MARKER_START);
    testTokenization("cat <<        \"END\"", WORD, WHITESPACE, HEREDOC_MARKER_TAG, WHITESPACE, HEREDOC_MARKER_START);
    testTokenization("cat <<        \"END\"\"END\"", WORD, WHITESPACE, HEREDOC_MARKER_TAG, WHITESPACE, HEREDOC_MARKER_START);
    testTokenization("cat <<        $\"END\"\"END\"", WORD, WHITESPACE, HEREDOC_MARKER_TAG, WHITESPACE, HEREDOC_MARKER_START);
    testTokenization("cat <<        $\"END\"$\"END\"", WORD, WHITESPACE, HEREDOC_MARKER_TAG, WHITESPACE, HEREDOC_MARKER_START);
    testTokenization("cat <<'END'", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START);
    testTokenization("cat <<        'END'", WORD, WHITESPACE, HEREDOC_MARKER_TAG, WHITESPACE, HEREDOC_MARKER_START);
    testTokenization("cat <<        $'END'", WORD, WHITESPACE, HEREDOC_MARKER_TAG, WHITESPACE, HEREDOC_MARKER_START);
    testTokenization("cat <<        $'END''END'", WORD, WHITESPACE, HEREDOC_MARKER_TAG, WHITESPACE, HEREDOC_MARKER_START);

    testTokenization("cat <<END", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START);
    testTokenization("cat <<END\n", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, LINE_FEED);
    testTokenization("cat <<END\nABC\n", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, LINE_FEED, HEREDOC_CONTENT);
    testTokenization("cat <<END\nABC\n\n", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, LINE_FEED, HEREDOC_CONTENT);
    testTokenization("cat <<END\nABC", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, LINE_FEED, HEREDOC_CONTENT);

    testTokenization("cat <<END\nABC\nDEF\nEND\n", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, LINE_FEED, HEREDOC_CONTENT, HEREDOC_MARKER_END, LINE_FEED);
    testTokenization("cat << END\nABC\nDEF\nEND\n", WORD, WHITESPACE, HEREDOC_MARKER_TAG, WHITESPACE, HEREDOC_MARKER_START, LINE_FEED, HEREDOC_CONTENT, HEREDOC_MARKER_END, LINE_FEED);

    testTokenization("cat <<-END\nABC\nDEF\nEND\n", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, LINE_FEED, HEREDOC_CONTENT, HEREDOC_MARKER_IGNORING_TABS_END, LINE_FEED);
    testTokenization("cat <<- END\nABC\nDEF\nEND\n", WORD, WHITESPACE, HEREDOC_MARKER_TAG, WHITESPACE, HEREDOC_MARKER_START, LINE_FEED, HEREDOC_CONTENT, HEREDOC_MARKER_IGNORING_TABS_END, LINE_FEED);

    testTokenization("cat <<END\nABC\nDEF\nEND", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, LINE_FEED, HEREDOC_CONTENT, HEREDOC_MARKER_END);

    testTokenization("cat <<END\nABC\nDEF\n\n\nXYZ DEF\nEND", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, LINE_FEED, HEREDOC_CONTENT, HEREDOC_MARKER_END);

    testTokenization("cat <<!\n!", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, LINE_FEED, HEREDOC_MARKER_END);

    testTokenization("{\n" +
        "cat <<EOF\n" +
        "test\n" +
        "EOF\n" +
        "}", LEFT_CURLY, LINE_FEED, WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, LINE_FEED, HEREDOC_CONTENT, HEREDOC_MARKER_END, LINE_FEED, RIGHT_CURLY);

    testTokenization("cat <<EOF\n" +
        "$test\n" +
        "EOF", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, LINE_FEED, VARIABLE, HEREDOC_CONTENT, HEREDOC_MARKER_END);

    testTokenization("{\n" +
        "cat <<EOF\n" +
        "$(test)\n" +
        "EOF\n" +
        "}", LEFT_CURLY, LINE_FEED, WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, LINE_FEED, DOLLAR, LEFT_PAREN, WORD, RIGHT_PAREN, HEREDOC_CONTENT, HEREDOC_MARKER_END, LINE_FEED, RIGHT_CURLY);

    testTokenization("if test\n" +
            "cat <<EOF\n" +
            "EOF\n" +
            "test\n" +
            "fi",
        IF_KEYWORD, WHITESPACE, WORD, LINE_FEED,
        WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, LINE_FEED,
        HEREDOC_MARKER_END, LINE_FEED,
        WORD, LINE_FEED,
        FI_KEYWORD);

    testTokenization("cat <<X <<\n", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, WHITESPACE, HEREDOC_MARKER_TAG, LINE_FEED);

    testTokenization("cat <<$\n$", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, LINE_FEED, HEREDOC_MARKER_END);
    testTokenization("cat <<$_X\n$_X", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, LINE_FEED, HEREDOC_MARKER_END);

    //tab prefixed end marker is valid if started by <<-
    //no tab
    testTokenization("cat <<-EOF\nEOF", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, LINE_FEED, HEREDOC_MARKER_IGNORING_TABS_END);
    testTokenization("cat <<- EOF\nEOF", WORD, WHITESPACE, HEREDOC_MARKER_TAG, WHITESPACE, HEREDOC_MARKER_START, LINE_FEED, HEREDOC_MARKER_IGNORING_TABS_END);
    //with tab
    testTokenization("cat <<-EOF\n\tEOF", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, LINE_FEED, HEREDOC_MARKER_IGNORING_TABS_END);
    testTokenization("cat <<- EOF\n\tEOF", WORD, WHITESPACE, HEREDOC_MARKER_TAG, WHITESPACE, HEREDOC_MARKER_START, LINE_FEED, HEREDOC_MARKER_IGNORING_TABS_END);
    //with tab but without <<-
    testTokenization("cat <<EOF\n\tEOF", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, LINE_FEED, HEREDOC_CONTENT);

    // subshell expression
    testTokenization("{\n" +
            "<<EOF <<EOF2\n" +
            "EOF\n" +
            "$(a)\n" +
            "EOF2", LEFT_CURLY, LINE_FEED, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, LINE_FEED,
        HEREDOC_MARKER_END, LINE_FEED,
        DOLLAR, LEFT_PAREN, WORD, RIGHT_PAREN, HEREDOC_CONTENT,
        HEREDOC_MARKER_END
    );

    //normal arithmetic expression
    testTokenization("{\n" +
            "<<EOF <<EOF2\n" +
            "EOF\n" +
            "$((a))\n" +
            "EOF2", LEFT_CURLY, LINE_FEED, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, LINE_FEED,
        HEREDOC_MARKER_END, LINE_FEED,
        DOLLAR, EXPR_ARITH, WORD, _EXPR_ARITH, HEREDOC_CONTENT,
        HEREDOC_MARKER_END
    );

    //square arithmetic expression
    testTokenization("{\n" +
            "<<EOF <<EOF2\n" +
            "EOF\n" +
            "$[a]\n" +
            "EOF2", LEFT_CURLY, LINE_FEED, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, LINE_FEED,
        HEREDOC_MARKER_END, LINE_FEED,
        DOLLAR, EXPR_ARITH_SQUARE, WORD, _EXPR_ARITH_SQUARE, HEREDOC_CONTENT,
        HEREDOC_MARKER_END
    );
  }

  @Test
  public void testMultilineHeredoc() {
    //multiple heredocs in one command line
    testTokenization("cat <<END <<END2\nABC\nEND\nABC\nEND2\n", WORD, WHITESPACE, HEREDOC_MARKER_TAG,
        HEREDOC_MARKER_START, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, LINE_FEED,
        HEREDOC_CONTENT, HEREDOC_MARKER_END, LINE_FEED, HEREDOC_CONTENT, HEREDOC_MARKER_END, LINE_FEED);
  }

  @Test
  @Ignore //ignored for now because there is a match-all rule for the heredoc start marker
  public void _testHeredocErrors() {
    testTokenization("cat <<\"END\"", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START);

    //the closing string marker is missing in the heredoc
    testTokenization("cat <<\"END", WORD, WHITESPACE, HEREDOC_MARKER_TAG, BAD_CHARACTER, BAD_CHARACTER);
  }

  @Test
  public void testIssue125() {
    testTokenization("read 'test' a[0]", WORD, WHITESPACE, STRING2, WHITESPACE, ASSIGNMENT_WORD, LEFT_SQUARE, ARITH_NUMBER, RIGHT_SQUARE);
  }

  @Test
  public void testIssue199() {
    testTokenization("$( ((count != 1)) && echo)", DOLLAR, LEFT_PAREN, WHITESPACE, EXPR_ARITH, WORD, WHITESPACE, ARITH_NE, WHITESPACE, ARITH_NUMBER, _EXPR_ARITH, WHITESPACE, AND_AND, WHITESPACE, WORD, RIGHT_PAREN);
    testTokenization("$(((count != 1)) && echo)", DOLLAR, LEFT_PAREN, EXPR_ARITH, WORD, WHITESPACE, ARITH_NE, WHITESPACE, ARITH_NUMBER, _EXPR_ARITH, WHITESPACE, AND_AND, WHITESPACE, WORD, RIGHT_PAREN);
    //limitation of the Bash lexer: no look-ahead to the end of an expression
    //Bash parses this (probably) as an arithmetic expression with a parenthesis inside
    //BashSupport doesn't
    testTokenization("(((1==1)))", LEFT_PAREN, EXPR_ARITH, ARITH_NUMBER, ARITH_EQ, ARITH_NUMBER, _EXPR_ARITH, RIGHT_PAREN);

    //the grammar is a bit complicated, the expression parsed beginning with $((( depends on the end of the expression
    //bash interprets the tokens $(((1+1)+1)) different than $(((1+1)) && echo)
    //the first is an arithmetic expression with a sum computation
    //the second is a subshell with an embedded arithmetic command and an echo command
    //if an expression starts with three or more parentheses the rule is:
    //  if the expression ends with a single parenthesis, then the first opening parenthesis opens a subshell
    //  if the expression ends with two parentheses, then the first two start an arithmetic command
  }

  @Test
  public void testIssue242() {
    testTokenization("eval \"$1=\\$(printf 'a' \\\"$1\\\")\"", WORD, WHITESPACE, STRING_BEGIN, VARIABLE, STRING_CONTENT, VARIABLE, STRING_CONTENT, STRING_END);
  }

  @Test
  public void testIssue243() {
    testNoErrors("foo() { foo=\"$1\"; shift; while [ $# -gt 0 ]; do case \"$1\" in ($foo) ;; (*) return 1;; esac; shift; done; }");
  }

  @Test
  public void testIssue246() {
    testTokenization("echo '\\';", WORD, WHITESPACE, STRING2, SEMI);
    testTokenization("echo '\\'; echo", WORD, WHITESPACE, STRING2, SEMI, WHITESPACE, WORD);
    testTokenization("echo '\\' && echo \\'", WORD, WHITESPACE, STRING2, WHITESPACE, AND_AND, WHITESPACE, WORD, WHITESPACE, WORD);

    testTokenization("$'hi \\' there'", STRING2);
  }

  @Test
  public void testIssue266() {
    testTokenization("${#}", DOLLAR, LEFT_CURLY, PARAM_EXPANSION_OP_HASH, RIGHT_CURLY);
  }

  @Test
  public void testIssue270() {
    //heredoc without evaluation
    testTokenization("cat <<'EOF'\n" +
        "    echo ${counter}\n" +
        "EOF", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, LINE_FEED, HEREDOC_CONTENT, HEREDOC_MARKER_END);

    //heredoc with evaluation
    testTokenization("cat <<EOF\n" +
        "    echo ${counter}\n" +
        "EOF", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, LINE_FEED, HEREDOC_CONTENT, DOLLAR, LEFT_CURLY, WORD, RIGHT_CURLY, HEREDOC_CONTENT, HEREDOC_MARKER_END);

    //heredoc with escaped variable
    testTokenization("cat <<EOF\n" +
        "\\$counter\n" +
        "EOF", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, LINE_FEED, HEREDOC_CONTENT, HEREDOC_MARKER_END);
    testTokenization("cat <<EOF\n" +
        "echo \\$counter\n" +
        "EOF", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, LINE_FEED, HEREDOC_CONTENT, HEREDOC_MARKER_END);
    testTokenization("cat <<EOF\n" +
        "echo \\\n" +
        "EOF", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, LINE_FEED, HEREDOC_CONTENT, HEREDOC_MARKER_END);
  }

  @Test
  public void testIssue272() {
    testTokenization("${#array_var[@]}", DOLLAR, LEFT_CURLY, PARAM_EXPANSION_OP_HASH, WORD, LEFT_SQUARE, PARAM_EXPANSION_OP_AT, RIGHT_SQUARE, RIGHT_CURLY);
  }

  @Test
  public void testIssue300() {
    testTokenization("case x in\nabc${a}) ;; esac",
        CASE_KEYWORD, WHITESPACE, WORD, WHITESPACE, WORD, LINE_FEED, WORD, DOLLAR, LEFT_CURLY, WORD, RIGHT_CURLY, RIGHT_PAREN, WHITESPACE, CASE_END, WHITESPACE, ESAC_KEYWORD);
  }

  @Test
  public void testIssue303() {
    testTokenization("a=(123)", ASSIGNMENT_WORD, EQ, LEFT_PAREN, WORD, RIGHT_PAREN);
    testTokenization("a+=(123)", ASSIGNMENT_WORD, ADD_EQ, LEFT_PAREN, WORD, RIGHT_PAREN);

    testTokenization("( a=(123) )", LEFT_PAREN, WHITESPACE, ASSIGNMENT_WORD, EQ, LEFT_PAREN, WORD, RIGHT_PAREN, WHITESPACE, RIGHT_PAREN);
    testTokenization("( a+=(123) )", LEFT_PAREN, WHITESPACE, ASSIGNMENT_WORD, ADD_EQ, LEFT_PAREN, WORD, RIGHT_PAREN, WHITESPACE, RIGHT_PAREN);

    testTokenization("$( a=(123) )", DOLLAR, LEFT_PAREN, WHITESPACE, ASSIGNMENT_WORD, EQ, LEFT_PAREN, WORD, RIGHT_PAREN, WHITESPACE, RIGHT_PAREN);
    testTokenization("$( a+=(123) )", DOLLAR, LEFT_PAREN, WHITESPACE, ASSIGNMENT_WORD, ADD_EQ, LEFT_PAREN, WORD, RIGHT_PAREN, WHITESPACE, RIGHT_PAREN);

    testTokenization("` a=(123) `", BACKQUOTE, WHITESPACE, ASSIGNMENT_WORD, EQ, LEFT_PAREN, WORD, RIGHT_PAREN, WHITESPACE, BACKQUOTE);
    testTokenization("` a+=(123) `", BACKQUOTE, WHITESPACE, ASSIGNMENT_WORD, ADD_EQ, LEFT_PAREN, WORD, RIGHT_PAREN, WHITESPACE, BACKQUOTE);

    testTokenization("\"$( a=(123))\"", STRING_BEGIN, DOLLAR, LEFT_PAREN, WHITESPACE, ASSIGNMENT_WORD, EQ, LEFT_PAREN, WORD, RIGHT_PAREN, RIGHT_PAREN, STRING_END);
    testTokenization("\"$( a+=(123))\"", STRING_BEGIN, DOLLAR, LEFT_PAREN, WHITESPACE, ASSIGNMENT_WORD, ADD_EQ, LEFT_PAREN, WORD, RIGHT_PAREN, RIGHT_PAREN, STRING_END);
  }

  @Test
  public void testIssue308() {
    testTokenization("[[ $# == \"x\" ]]", BRACKET_KEYWORD, VARIABLE, WHITESPACE, COND_OP_EQ_EQ, WHITESPACE, STRING_BEGIN, STRING_CONTENT, STRING_END, _BRACKET_KEYWORD);

    testTokenization("[[ ( $# == \"x\" ) ]]", BRACKET_KEYWORD, LEFT_PAREN, WHITESPACE, VARIABLE, WHITESPACE, COND_OP_EQ_EQ, WHITESPACE, STRING_BEGIN, STRING_CONTENT, STRING_END, WHITESPACE, RIGHT_PAREN, _BRACKET_KEYWORD);

    testTokenization("[[ ( $# == \"x\" ) || -f \"x\" || 123==123 ]]", BRACKET_KEYWORD, LEFT_PAREN, WHITESPACE, VARIABLE, WHITESPACE, COND_OP_EQ_EQ, WHITESPACE, STRING_BEGIN, STRING_CONTENT, STRING_END, WHITESPACE, RIGHT_PAREN, WHITESPACE, OR_OR, WHITESPACE, COND_OP, WHITESPACE, STRING_BEGIN, STRING_CONTENT, STRING_END, WHITESPACE, OR_OR, WHITESPACE, WORD, COND_OP_EQ_EQ, WORD, _BRACKET_KEYWORD);
  }

  @Test
  public void testIssue89() {
    testTokenization("function a {\n}function", FUNCTION_KEYWORD, WHITESPACE, WORD, WHITESPACE, LEFT_CURLY, LINE_FEED, RIGHT_CURLY, FUNCTION_KEYWORD);

  }

  @Test
  public void testIssue320() {
    testTokenization("(( a[0] ))", EXPR_ARITH, WHITESPACE, ASSIGNMENT_WORD, LEFT_SQUARE, ARITH_NUMBER, RIGHT_SQUARE, WHITESPACE, _EXPR_ARITH);
    testTokenization("(( A[0] ))", EXPR_ARITH, WHITESPACE, ASSIGNMENT_WORD, LEFT_SQUARE, ARITH_NUMBER, RIGHT_SQUARE, WHITESPACE, _EXPR_ARITH);

    testTokenization("(( a[0x0] ))", EXPR_ARITH, WHITESPACE, ASSIGNMENT_WORD, LEFT_SQUARE, ARITH_HEX_NUMBER, RIGHT_SQUARE, WHITESPACE, _EXPR_ARITH);
    testTokenization("(( A[0x0] ))", EXPR_ARITH, WHITESPACE, ASSIGNMENT_WORD, LEFT_SQUARE, ARITH_HEX_NUMBER, RIGHT_SQUARE, WHITESPACE, _EXPR_ARITH);

    testTokenization("(( a[a[b]] ))", EXPR_ARITH, WHITESPACE, ASSIGNMENT_WORD, LEFT_SQUARE, ASSIGNMENT_WORD, LEFT_SQUARE, WORD, RIGHT_SQUARE, RIGHT_SQUARE, WHITESPACE, _EXPR_ARITH);
    testTokenization("(( a[a[0]] ))", EXPR_ARITH, WHITESPACE, ASSIGNMENT_WORD, LEFT_SQUARE, ASSIGNMENT_WORD, LEFT_SQUARE, ARITH_NUMBER, RIGHT_SQUARE, RIGHT_SQUARE, WHITESPACE, _EXPR_ARITH);

    testTokenization("(( A[A[0]] ))", EXPR_ARITH, WHITESPACE, ASSIGNMENT_WORD, LEFT_SQUARE, ASSIGNMENT_WORD, LEFT_SQUARE, ARITH_NUMBER, RIGHT_SQUARE, RIGHT_SQUARE, WHITESPACE, _EXPR_ARITH);
  }

  @Test
  public void testIssue325() {
    testTokenization("cat << USE\nUSE", WORD, WHITESPACE, HEREDOC_MARKER_TAG, WHITESPACE, HEREDOC_MARKER_START, LINE_FEED, HEREDOC_MARKER_END);
    testTokenization("cat << THEUSAGE\nTHEUSAGE", WORD, WHITESPACE, HEREDOC_MARKER_TAG, WHITESPACE, HEREDOC_MARKER_START, LINE_FEED, HEREDOC_MARKER_END);
    //theUsage triggers a JFlex bug (apparently) if the marker regex includes [\s]
    testTokenization("cat << theUsage\ntheUsage", WORD, WHITESPACE, HEREDOC_MARKER_TAG, WHITESPACE, HEREDOC_MARKER_START, LINE_FEED, HEREDOC_MARKER_END);
    testTokenization("cat << _theUsage\n_theUsage", WORD, WHITESPACE, HEREDOC_MARKER_TAG, WHITESPACE, HEREDOC_MARKER_START, LINE_FEED, HEREDOC_MARKER_END);
  }

  @Test
  public void testIssue327() {
    testTokenization("<< EOF\n\\$(a)\nEOF", HEREDOC_MARKER_TAG, WHITESPACE, HEREDOC_MARKER_START, LINE_FEED, HEREDOC_CONTENT, HEREDOC_MARKER_END);
  }

  @Test
  public void testIssue330() {
    testTokenization("eval \"$a=()\"", WORD, WHITESPACE, STRING_BEGIN, VARIABLE, STRING_CONTENT, STRING_END);
  }

  @Test
  public void testIssue330Var() {
    testTokenization("eval \"\\${$a}\"", WORD, WHITESPACE, STRING_BEGIN, STRING_CONTENT, VARIABLE, STRING_CONTENT, STRING_END);
  }

  @Test
  public void testIssue341() {
    testTokenization("\"`echo \"$0\"`\"", STRING_BEGIN, BACKQUOTE, WORD, WHITESPACE, STRING_BEGIN, VARIABLE, STRING_END, BACKQUOTE, STRING_END);
    testTokenization("(cd \"`dirname \"$0\"`\")", LEFT_PAREN, WORD, WHITESPACE, STRING_BEGIN, BACKQUOTE, WORD, WHITESPACE, STRING_BEGIN, VARIABLE, STRING_END, BACKQUOTE, STRING_END, RIGHT_PAREN);
  }

  @Test
  public void testIssue343() {
    testTokenization("{\n" +
            "<<EOF <<EOF2\n" +
            "EOF\n" +
            "${}\n" +
            "EOF2\n" +
            "}\n" +
            "$1", LEFT_CURLY, LINE_FEED, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, LINE_FEED,
        HEREDOC_MARKER_END, LINE_FEED,
        DOLLAR, LEFT_CURLY, RIGHT_CURLY, HEREDOC_CONTENT,
        HEREDOC_MARKER_END, LINE_FEED,
        RIGHT_CURLY, LINE_FEED,
        VARIABLE
    );

    //incomplete ${ in a heredoc
    testTokenization("{\n" +
            "<<EOF <<EOF2\n" +
            "EOF\n" +
            "${\n" +
            "EOF2\n" +
            "}\n" +
            "$1", LEFT_CURLY, LINE_FEED, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, LINE_FEED,
        HEREDOC_MARKER_END, LINE_FEED,
        DOLLAR, LEFT_CURLY, LINE_FEED,
        HEREDOC_MARKER_END, LINE_FEED,
        RIGHT_CURLY, LINE_FEED,
        VARIABLE);

    //incomplete $( in a heredoc
    testTokenization("{\n" +
            "<<EOF <<EOF2\n" +
            "EOF\n" +
            "$(\n" +
            "EOF2\n" +
            ")\n" +
            "$1", LEFT_CURLY, LINE_FEED, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, LINE_FEED,
        HEREDOC_MARKER_END, LINE_FEED,
        DOLLAR, LEFT_PAREN, LINE_FEED,
        HEREDOC_MARKER_END, LINE_FEED,
        RIGHT_PAREN, LINE_FEED,
        VARIABLE
    );

    //incomplete $(( in a heredoc
    testTokenization("{\n" +
            "<<EOF <<EOF2\n" +
            "EOF\n" +
            "$((\n" +
            "EOF2\n" +
            ")\n" +
            "$1", LEFT_CURLY, LINE_FEED, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, LINE_FEED,
        HEREDOC_MARKER_END, LINE_FEED,
        DOLLAR, EXPR_ARITH, LINE_FEED,
        HEREDOC_MARKER_END, LINE_FEED,
        RIGHT_PAREN, LINE_FEED,
        VARIABLE
    );
  }

  @Test
  public void testIssue354() {
    testTokenization("${var##abc}", DOLLAR, LEFT_CURLY, WORD, PARAM_EXPANSION_OP_HASH_HASH, WORD, RIGHT_CURLY);
  }

  @Test
  public void testIssue389() {
    testTokenization("a\\\nb", WORD);
    testTokenization("a\\\n", WORD);
    testTokenization("\\\nb", WORD);
  }

  @Test
  public void testTrapLexing() {
    testTokenization("trap", TRAP_KEYWORD);
    testTokenization("trap -l", TRAP_KEYWORD, WHITESPACE, WORD);
    testTokenization("trap -lp", TRAP_KEYWORD, WHITESPACE, WORD);
    testTokenization("trap FUNCTION", TRAP_KEYWORD, WHITESPACE, WORD);
    testTokenization("trap FUNCTION SIGINT", TRAP_KEYWORD, WHITESPACE, WORD, WHITESPACE, WORD);
    testTokenization("trap -p SIGINT", TRAP_KEYWORD, WHITESPACE, WORD, WHITESPACE, WORD);
  }

  @Test
  public void testEvalLexing() {
    testTokenization("$a=$a", VARIABLE, EQ, VARIABLE);
  }

  @Test
  public void testIssue376() {
    testTokenization("$(echo 2>&1)", DOLLAR, LEFT_PAREN, WORD, WHITESPACE, INTEGER_LITERAL, GREATER_THAN, FILEDESCRIPTOR, RIGHT_PAREN);
    testTokenization("[[ $(echo 2>&1) ]]", BRACKET_KEYWORD, DOLLAR, LEFT_PAREN, WORD, WHITESPACE, INTEGER_LITERAL, GREATER_THAN, FILEDESCRIPTOR, RIGHT_PAREN, _BRACKET_KEYWORD);
  }

  @Test
  public void testIssue367() {
    //invalid command semantic, but lexing needs to work
    testTokenization("[ (echo a) ]", EXPR_CONDITIONAL, LEFT_PAREN, WORD, WHITESPACE, WORD, RIGHT_PAREN, _EXPR_CONDITIONAL);

    testTokenization("[[ $(< $1) ]]", BRACKET_KEYWORD, DOLLAR, LEFT_PAREN, LESS_THAN, WHITESPACE, VARIABLE, RIGHT_PAREN, _BRACKET_KEYWORD);

    testTokenization("[[ $((1+1)) ]]", BRACKET_KEYWORD, DOLLAR, EXPR_ARITH, ARITH_NUMBER, ARITH_PLUS, ARITH_NUMBER, _EXPR_ARITH, _BRACKET_KEYWORD);
  }

  @Test
  public void testIssue418() {
    testTokenization("foo=(${foo[@]%% (*})", ASSIGNMENT_WORD, EQ, LEFT_PAREN, DOLLAR, LEFT_CURLY, WORD, LEFT_SQUARE, PARAM_EXPANSION_OP_AT, RIGHT_SQUARE, PARAM_EXPANSION_OP_PERCENT, PARAM_EXPANSION_OP_PERCENT, WHITESPACE, LEFT_PAREN, WORD, RIGHT_CURLY, RIGHT_PAREN);
  }

  @Test
  public void testHereString() {
    testTokenization("a <<< a", WORD, WHITESPACE, REDIRECT_HERE_STRING, WHITESPACE, WORD);
    testTokenization("a <<< a_b", WORD, WHITESPACE, REDIRECT_HERE_STRING, WHITESPACE, WORD);
    testTokenization("a <<< a b", WORD, WHITESPACE, REDIRECT_HERE_STRING, WHITESPACE, WORD, WHITESPACE, WORD);

    testTokenization("a <<<a", WORD, WHITESPACE, REDIRECT_HERE_STRING, WORD);
    testTokenization("a <<<a_b", WORD, WHITESPACE, REDIRECT_HERE_STRING, WORD);
    testTokenization("a <<<a b", WORD, WHITESPACE, REDIRECT_HERE_STRING, WORD, WHITESPACE, WORD);

    //string content
    testTokenization("a <<<'abc'", WORD, WHITESPACE, REDIRECT_HERE_STRING, STRING2);
    testTokenization("a <<< 'abc'", WORD, WHITESPACE, REDIRECT_HERE_STRING, WHITESPACE, STRING2);
    testTokenization("a <<<\"a\"", WORD, WHITESPACE, REDIRECT_HERE_STRING, STRING_BEGIN, STRING_CONTENT, STRING_END);
    testTokenization("a <<< \"a\"", WORD, WHITESPACE, REDIRECT_HERE_STRING, WHITESPACE, STRING_BEGIN, STRING_CONTENT, STRING_END);

    //backticks
    testTokenization("a <<<`abc`", WORD, WHITESPACE, REDIRECT_HERE_STRING, BACKQUOTE, WORD, BACKQUOTE);
    testTokenization("a <<< `abc`", WORD, WHITESPACE, REDIRECT_HERE_STRING, WHITESPACE, BACKQUOTE, WORD, BACKQUOTE);

    //subshell
    testTokenization("a <<<$(abc)", WORD, WHITESPACE, REDIRECT_HERE_STRING, DOLLAR, LEFT_PAREN, WORD, RIGHT_PAREN);
    testTokenization("a <<< $(abc)", WORD, WHITESPACE, REDIRECT_HERE_STRING, WHITESPACE, DOLLAR, LEFT_PAREN, WORD, RIGHT_PAREN);
    testTokenization("$(a <<< [abc])", DOLLAR, LEFT_PAREN, WORD, WHITESPACE, REDIRECT_HERE_STRING, WHITESPACE, WORD, WORD, RIGHT_PAREN);

    //words
    testTokenization("a <<< {}", WORD, WHITESPACE, REDIRECT_HERE_STRING, WHITESPACE, WORD, WORD);
    testTokenization("a <<< {a}", WORD, WHITESPACE, REDIRECT_HERE_STRING, WHITESPACE, WORD, WORD, WORD);
    testTokenization("a <<< []", WORD, WHITESPACE, REDIRECT_HERE_STRING, WHITESPACE, WORD, WORD);
    testTokenization("a <<< [a]", WORD, WHITESPACE, REDIRECT_HERE_STRING, WHITESPACE, WORD, WORD);
    testTokenization("a <<< [$a", WORD, WHITESPACE, REDIRECT_HERE_STRING, WHITESPACE, WORD, VARIABLE);
    testTokenization("a <<< [$a]", WORD, WHITESPACE, REDIRECT_HERE_STRING, WHITESPACE, WORD, VARIABLE, WORD);
    testTokenization("a <<< [${a}]", WORD, WHITESPACE, REDIRECT_HERE_STRING, WHITESPACE, WORD, DOLLAR, LEFT_CURLY, WORD, RIGHT_CURLY, WORD);
    testTokenization("a <<< [$(a)]", WORD, WHITESPACE, REDIRECT_HERE_STRING, WHITESPACE, WORD, DOLLAR, LEFT_PAREN, WORD, RIGHT_PAREN, WORD);
    testTokenization("a <<< [$((1))]", WORD, WHITESPACE, REDIRECT_HERE_STRING, WHITESPACE, WORD, DOLLAR, EXPR_ARITH, ARITH_NUMBER, _EXPR_ARITH, WORD);

    //comment after here string
    testTokenization("read <<< x\n#comment", WORD, WHITESPACE, REDIRECT_HERE_STRING, WHITESPACE, WORD, LINE_FEED, COMMENT);
    testTokenization("read <<< \"x\"\n#comment", WORD, WHITESPACE, REDIRECT_HERE_STRING, WHITESPACE, STRING_BEGIN, STRING_CONTENT, STRING_END, LINE_FEED, COMMENT);
    testTokenization("read <<< \"x\"\nif", WORD, WHITESPACE, REDIRECT_HERE_STRING, WHITESPACE, STRING_BEGIN, STRING_CONTENT, STRING_END, LINE_FEED, IF_KEYWORD);
    testTokenization("read <<< \"x\"\na", WORD, WHITESPACE, REDIRECT_HERE_STRING, WHITESPACE, STRING_BEGIN, STRING_CONTENT, STRING_END, LINE_FEED, WORD);
    testTokenization("read <<< x <<< a", WORD, WHITESPACE, REDIRECT_HERE_STRING, WHITESPACE, WORD, WHITESPACE, REDIRECT_HERE_STRING, WHITESPACE, WORD);
    testTokenization("read <<< \"x\" <<< a", WORD, WHITESPACE, REDIRECT_HERE_STRING, WHITESPACE, STRING_BEGIN, STRING_CONTENT, STRING_END, WHITESPACE, REDIRECT_HERE_STRING, WHITESPACE, WORD);

    //following commands
    testTokenization("mysql <<<'CREATE DATABASE dev' || exit", WORD, WHITESPACE, REDIRECT_HERE_STRING, STRING2, WHITESPACE, OR_OR, WHITESPACE, WORD);
    testTokenization("mysql <<<'CREATE DATABASE dev' && exit", WORD, WHITESPACE, REDIRECT_HERE_STRING, STRING2, WHITESPACE, AND_AND, WHITESPACE, WORD);
    testTokenization("mysql <<<'CREATE DATABASE dev'||exit", WORD, WHITESPACE, REDIRECT_HERE_STRING, STRING2, OR_OR, WORD);
    testTokenization("mysql <<<'CREATE DATABASE dev'&&exit", WORD, WHITESPACE, REDIRECT_HERE_STRING, STRING2, AND_AND, WORD);

    testTokenization("mysql <<<'abc'; exit", WORD, WHITESPACE, REDIRECT_HERE_STRING, STRING2, SEMI, WHITESPACE, WORD);
    testTokenization("mysql <<<'abc';exit", WORD, WHITESPACE, REDIRECT_HERE_STRING, STRING2, SEMI, WORD);

    testTokenization("grep x <<< 'X' >/dev/null && echo 'Found' || echo 'Not found'",
        WORD, WHITESPACE, WORD, WHITESPACE, REDIRECT_HERE_STRING, WHITESPACE, STRING2, WHITESPACE, GREATER_THAN, WORD, WHITESPACE, AND_AND, WHITESPACE, WORD, WHITESPACE, STRING2, WHITESPACE, OR_OR, WHITESPACE, WORD, WHITESPACE, STRING2);
    testTokenization("grep x <<<$var >/dev/null && echo 'Found' || echo 'Not found'",
        WORD, WHITESPACE, WORD, WHITESPACE, REDIRECT_HERE_STRING, VARIABLE, WHITESPACE, GREATER_THAN, WORD, WHITESPACE, AND_AND, WHITESPACE, WORD, WHITESPACE, STRING2, WHITESPACE, OR_OR, WHITESPACE, WORD, WHITESPACE, STRING2);

    //invalid syntax
    testTokenization("a <<< (a)", WORD, WHITESPACE, REDIRECT_HERE_STRING, WHITESPACE, LEFT_PAREN, WORD, RIGHT_PAREN);
    testTokenization("a <<< \" [$a]", WORD, WHITESPACE, REDIRECT_HERE_STRING, WHITESPACE, STRING_BEGIN, STRING_CONTENT, VARIABLE, STRING_CONTENT);
  }

  @Test
  public void testUnicode() {
    testTokenization("разработка программного обеспечения", WORD, WHITESPACE, WORD, WHITESPACE, WORD);
    testTokenization("ανάπτυξη λογισμικού", WORD, WHITESPACE, WORD);
    testTokenization("פיתוח תוכנה", WORD, WHITESPACE, WORD);

    testTokenization("разработка программного обеспечения 2>&1", WORD, WHITESPACE, WORD, WHITESPACE, WORD, WHITESPACE, INTEGER_LITERAL, GREATER_THAN, FILEDESCRIPTOR);

    testTokenization("α=1", ASSIGNMENT_WORD, EQ, INTEGER_LITERAL);
    testTokenization("export α=1", WORD, WHITESPACE, ASSIGNMENT_WORD, EQ, INTEGER_LITERAL);
  }

  @Test
  public void testLineContinuation() {
        /* case x in
                a|\
                b)
                return
                        ;;
            esac
        */
    testTokenization("case x in\n" +
            "a|\\\n" +
            "b)\n" +
            "return\n" +
            ";;\n" +
            "esac",
        CASE_KEYWORD, WHITESPACE, WORD, WHITESPACE, WORD, LINE_FEED,
        WORD, PIPE, LINE_CONTINUATION, WORD, RIGHT_PAREN, LINE_FEED,
        WORD, LINE_FEED,
        CASE_END, LINE_FEED,
        ESAC_KEYWORD);

        /* case x in
                \
                a\
                |b\
                  cde)
                return
                        ;;
            esac
        */
    testTokenization("case x in\n" +
            "\\\na|\\\n" +
            " b\\\ncde)\n" +
            "return\n" +
            ";;\n" +
            "esac",
        CASE_KEYWORD, WHITESPACE, WORD, WHITESPACE, WORD, LINE_FEED,
        LINE_CONTINUATION, WORD, PIPE, LINE_CONTINUATION, WHITESPACE, WORD, RIGHT_PAREN, LINE_FEED,
        WORD, LINE_FEED,
        CASE_END, LINE_FEED,
        ESAC_KEYWORD);
  }

  @Test
  public void testIssue358() {
    //the problem with #398 was, that the lexer had a bad rule to leave unmatched characters and not return BAD_CHARACTER for all states at the end
    testTokenization("b & << EOF\n" +
        "d\n" +
        "EOF", WORD, WHITESPACE, AMP, WHITESPACE, HEREDOC_MARKER_TAG, WHITESPACE, HEREDOC_MARKER_START, LINE_FEED, HEREDOC_CONTENT, HEREDOC_MARKER_END);
  }

  @Test
  public void testIssue398() {
    //the problem with #398 was, that the lexer had a bad rule to leave unmatched characters and not return BAD_CHARACTER for all states at the end
    testTokenization("$(${)", DOLLAR, LEFT_PAREN, DOLLAR, LEFT_CURLY, BAD_CHARACTER);
  }

  @Test
  public void testIssue426() {
    // ,, is the lowercase operator for all characters
    testTokenization("${var1,}", DOLLAR, LEFT_CURLY, WORD, PARAM_EXPANSION_OP_LOWERCASE_FIRST, RIGHT_CURLY);
    testTokenization("${var1,,}", DOLLAR, LEFT_CURLY, WORD, PARAM_EXPANSION_OP_LOWERCASE_ALL, RIGHT_CURLY);

    testTokenization("${var1^}", DOLLAR, LEFT_CURLY, WORD, PARAM_EXPANSION_OP_UPPERCASE_FIRST, RIGHT_CURLY);
    testTokenization("${var1^^}", DOLLAR, LEFT_CURLY, WORD, PARAM_EXPANSION_OP_UPPERCASE_ALL, RIGHT_CURLY);
  }

  @Test
  public void testIssue431() {
    //the problem with #398 was, that the lexer had a bad rule to leave unmatched characters and not return BAD_CHARACTER for all states at the end
    testTokenization("$((x|=5))", DOLLAR, EXPR_ARITH, WORD, ARITH_ASS_BIT_OR, ARITH_NUMBER, _EXPR_ARITH);
    testTokenization("$((x&=5))", DOLLAR, EXPR_ARITH, WORD, ARITH_ASS_BIT_AND, ARITH_NUMBER, _EXPR_ARITH);
    testTokenization("$((x^=5))", DOLLAR, EXPR_ARITH, WORD, ARITH_ASS_BIT_XOR, ARITH_NUMBER, _EXPR_ARITH);
  }

  @Test
  public void testIssue419() {
    testTokenization("$(x || x)", DOLLAR, LEFT_PAREN, WORD, WHITESPACE, OR_OR, WHITESPACE, WORD, RIGHT_PAREN);

    testTokenization("issues=($(x || x))", ASSIGNMENT_WORD, EQ, LEFT_PAREN, DOLLAR, LEFT_PAREN, WORD, WHITESPACE, OR_OR, WHITESPACE, WORD, RIGHT_PAREN, RIGHT_PAREN);
    testTokenization("issues=($(x && x))", ASSIGNMENT_WORD, EQ, LEFT_PAREN, DOLLAR, LEFT_PAREN, WORD, WHITESPACE, AND_AND, WHITESPACE, WORD, RIGHT_PAREN, RIGHT_PAREN);

    testTokenization("issues=($((1+1)))", ASSIGNMENT_WORD, EQ, LEFT_PAREN, DOLLAR, EXPR_ARITH, ARITH_NUMBER, ARITH_PLUS, ARITH_NUMBER, _EXPR_ARITH, RIGHT_PAREN);
  }

  @Test
  public void testIssue412() {
    testTokenization("[[ (a =~ \"b\") ]]", BRACKET_KEYWORD, LEFT_PAREN, WORD, WHITESPACE, COND_OP_REGEX, WHITESPACE, STRING_BEGIN, STRING_CONTENT, STRING_END, RIGHT_PAREN, _BRACKET_KEYWORD);
  }

  @Test
  public void testIssue401() {
    //less-than should be replaced with a better token in the lexer
    testTokenization("\"${A%<}\"", STRING_BEGIN, DOLLAR, LEFT_CURLY, WORD, PARAM_EXPANSION_OP_PERCENT, LESS_THAN, RIGHT_CURLY, STRING_END);
  }

  @Test
  public void testIssue457() {
        /*
        a="a\
        b"
        $a
        */
    testTokenization("a=\"a\\b_\"\n$a", ASSIGNMENT_WORD, EQ, STRING_BEGIN, STRING_CONTENT, STRING_END, LINE_FEED, VARIABLE);

        /*
        a="a"\
        "b_"
        $a
        */
    testTokenization("a=\"a\"\\\n\"b_\"\n$a", ASSIGNMENT_WORD, EQ, STRING_BEGIN, STRING_CONTENT, STRING_END, LINE_CONTINUATION, STRING_BEGIN, STRING_CONTENT, STRING_END, LINE_FEED, VARIABLE);
  }

  @Test
  public void testIssue469() {
    testTokenization(BashVersion.V3, "(a) |& a b", LEFT_PAREN, WORD, RIGHT_PAREN, WHITESPACE, PIPE, AMP, WHITESPACE, WORD, WHITESPACE, WORD);
    testTokenization(BashVersion.V4, "(a) |& a b", LEFT_PAREN, WORD, RIGHT_PAREN, WHITESPACE, PIPE_AMP, WHITESPACE, WORD, WHITESPACE, WORD);
  }

  @Test
  public void testIssue473() {
    // `cat <<EOF
    // X
    // EOF`
    testTokenization("`cat <<EOF\nX\nEOF`", BACKQUOTE, WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, LINE_FEED, HEREDOC_CONTENT, HEREDOC_MARKER_END, BACKQUOTE);

    // $(cat <<EOF
    // X
    // EOF
    // )
    testTokenization("$(cat <<EOF\nX\nEOF\n)", DOLLAR, LEFT_PAREN, WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, LINE_FEED, HEREDOC_CONTENT, HEREDOC_MARKER_END, LINE_FEED, RIGHT_PAREN);
  }

  @Test
  public void testIssue474() {
    //less-than should be replaced with a better token in the lexer
    testTokenization("cat <<EOF;\nX\nEOF", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, SEMI, LINE_FEED, HEREDOC_CONTENT, HEREDOC_MARKER_END);
  }

  @Test
  public void testIssue505() {
    testTokenization("x<<<${2}", WORD, REDIRECT_HERE_STRING, DOLLAR, LEFT_CURLY, WORD, RIGHT_CURLY);
    testTokenization("x<<<${2}>/dev/null", WORD, REDIRECT_HERE_STRING, DOLLAR, LEFT_CURLY, WORD, RIGHT_CURLY, GREATER_THAN, WORD);

    testTokenization("foo() {\nif ! grep $1 <<< ${2} > /dev/null; then echo Boom; fi\n}",
        WORD, LEFT_PAREN, RIGHT_PAREN, WHITESPACE, LEFT_CURLY,
        LINE_FEED, IF_KEYWORD, WHITESPACE, BANG_TOKEN, WHITESPACE, WORD, WHITESPACE, VARIABLE, WHITESPACE,
        REDIRECT_HERE_STRING, WHITESPACE, DOLLAR, LEFT_CURLY, WORD, RIGHT_CURLY, WHITESPACE,
        GREATER_THAN, WHITESPACE, WORD, SEMI, WHITESPACE,
        THEN_KEYWORD, WHITESPACE, WORD, WHITESPACE, WORD, SEMI, WHITESPACE, FI_KEYWORD, LINE_FEED, RIGHT_CURLY);
  }

  private void testNoErrors(String code) {
    BashLexer lexer = new BashLexer(BashVersion.V4);
    lexer.start(code);

    int i = 1;
    while (lexer.getTokenType() != null) {
      assertNotSame("Error token found at position " + i, lexer.getTokenType(), BAD_CHARACTER);

      i++;
      lexer.advance();
    }
  }

  private void testTokenization(String code, IElementType... expectedTokens) {
    testTokenization(BashVersion.V3, code, expectedTokens);
  }

  private void testTokenization(BashVersion version, String code, IElementType... expectedTokens) {
    BashLexer lexer = new BashLexer(version);
    lexer.start(code);

    int i = 1;
    for (IElementType expectedToken : expectedTokens) {
      IElementType tokenType = lexer.getTokenType();
      Assert.assertEquals("Wrong match at #" + i,
          expectedToken, tokenType);
      lexer.advance();
      ++i;
    }

    //check if the lexer has tokens left
    boolean noTokensLeft = lexer.getTokenType() == null;
    if (!noTokensLeft) {
      List<IElementType> tokens = Lists.newLinkedList();
      while (lexer.getTokenType() != null) {
        tokens.add(lexer.getTokenType());
        lexer.advance();
      }

      Assert.assertTrue("Lexer has tokens left: " + Iterables.toString(tokens), noTokensLeft);
    }
  }
}
