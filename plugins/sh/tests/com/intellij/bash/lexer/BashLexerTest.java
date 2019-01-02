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
    testTokenization("# Text\n", COMMENT, BashTokenTypes.LINEFEED);
    testTokenization("a #!b", WORD, WHITESPACE, SHEBANG);
    testTokenization("#a\n#b\n", COMMENT, BashTokenTypes.LINEFEED, COMMENT, BashTokenTypes.LINEFEED);
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
    testTokenization("a=1 b=2 echo", ASSIGNMENT_WORD, EQ, INT, WHITESPACE, ASSIGNMENT_WORD, EQ, INT, WHITESPACE, WORD);
    testTokenization("a=1 b=2", ASSIGNMENT_WORD, EQ, INT, WHITESPACE, ASSIGNMENT_WORD, EQ, INT);
    testTokenization("a+=a", ASSIGNMENT_WORD, ADD_EQ, WORD);
    testTokenization("if a; then PIDDIR=a$(a) a; fi", IF, WHITESPACE, WORD, SEMI, WHITESPACE, THEN, WHITESPACE, ASSIGNMENT_WORD, EQ, WORD, DOLLAR, LEFT_PAREN, WORD, RIGHT_PAREN, WHITESPACE, WORD, SEMI, WHITESPACE, FI);

    //line continuation token is ignored
    testTokenization("a=a\\\nb", ASSIGNMENT_WORD, EQ, WORD);

    testTokenization("[ $(uname -a) ]", EXPR_CONDITIONAL_LEFT, DOLLAR, LEFT_PAREN, WORD, WHITESPACE, WORD, RIGHT_PAREN, EXPR_CONDITIONAL_RIGHT);
  }

  @Test
  public void testArrayVariables() {
    testTokenization("${PIPESTATUS[0]}", DOLLAR, LEFT_CURLY, WORD, LEFT_SQUARE, BashTokenTypes.NUMBER, RIGHT_SQUARE, RIGHT_CURLY);

    testTokenization("${#myVar[*]}", DOLLAR, LEFT_CURLY, PARAM_EXPANSION_OP_HASH, WORD, LEFT_SQUARE, PARAM_EXPANSION_OP_STAR, RIGHT_SQUARE, RIGHT_CURLY);

    testTokenization("${myVar[0]:1}", DOLLAR, LEFT_CURLY, WORD, LEFT_SQUARE, BashTokenTypes.NUMBER, RIGHT_SQUARE, PARAM_EXPANSION_OP_COLON, WORD, RIGHT_CURLY);

    testTokenization("${myVar[*]:1}", DOLLAR, LEFT_CURLY, WORD, LEFT_SQUARE, PARAM_EXPANSION_OP_STAR, RIGHT_SQUARE, PARAM_EXPANSION_OP_COLON, WORD, RIGHT_CURLY);

    testTokenization("${myVar[@]:1}", DOLLAR, LEFT_CURLY, WORD, LEFT_SQUARE, PARAM_EXPANSION_OP_AT, RIGHT_SQUARE, PARAM_EXPANSION_OP_COLON, WORD, RIGHT_CURLY);

    testTokenization("a=( one two three)", ASSIGNMENT_WORD, EQ, LEFT_PAREN, WHITESPACE, WORD, WHITESPACE, WORD, WHITESPACE, WORD, RIGHT_PAREN);

    testTokenization("a=( one two [2]=three)", ASSIGNMENT_WORD, EQ, LEFT_PAREN, WHITESPACE, WORD, WHITESPACE, WORD, WHITESPACE, LEFT_SQUARE, BashTokenTypes.NUMBER, RIGHT_SQUARE, EQ, WORD, RIGHT_PAREN);

    testTokenization("a=(1 2 3)", ASSIGNMENT_WORD, EQ, LEFT_PAREN, WORD, WHITESPACE, WORD, WHITESPACE, WORD, RIGHT_PAREN);

    testTokenization("a[1]=", ASSIGNMENT_WORD, LEFT_SQUARE, BashTokenTypes.NUMBER, RIGHT_SQUARE, EQ);
  }

  @Test
  public void testArrayWithString() {
    // ARR=(['foo']='someval' ['bar']='otherval')

    testTokenization("a=(['x']=1)", ASSIGNMENT_WORD, EQ, LEFT_PAREN, LEFT_SQUARE, STRING2, RIGHT_SQUARE, EQ, WORD, RIGHT_PAREN);
  }

  @Test
  public void testSquareBracketArithmeticExpr() {
    testTokenization("$[1]", DOLLAR, EXPR_ARITH_SQUARE, BashTokenTypes.NUMBER, _EXPR_ARITH_SQUARE);

    testTokenization("$[1 ]", DOLLAR, EXPR_ARITH_SQUARE, BashTokenTypes.NUMBER, WHITESPACE, _EXPR_ARITH_SQUARE);

    testTokenization("$[1/${a}]", DOLLAR, EXPR_ARITH_SQUARE, BashTokenTypes.NUMBER, DIV, DOLLAR, LEFT_CURLY, WORD, RIGHT_CURLY, _EXPR_ARITH_SQUARE);

    //lexable, but bad syntax
    testTokenization("$(([1]))", DOLLAR, EXPR_ARITH, LEFT_SQUARE, BashTokenTypes.NUMBER, RIGHT_SQUARE, _EXPR_ARITH);
  }

  @Test
  public void testArithmeticExpr() {
    testTokenization("$((1))", DOLLAR, EXPR_ARITH, BashTokenTypes.NUMBER, _EXPR_ARITH);

    testTokenization("$((1,1))", DOLLAR, EXPR_ARITH, BashTokenTypes.NUMBER, COMMA, BashTokenTypes.NUMBER, _EXPR_ARITH);

    testTokenization("$((1/${a}))", DOLLAR, EXPR_ARITH, BashTokenTypes.NUMBER, DIV, DOLLAR, LEFT_CURLY, WORD, RIGHT_CURLY, _EXPR_ARITH);

    testTokenization("$((a=1,1))", DOLLAR, EXPR_ARITH, ASSIGNMENT_WORD, EQ, BashTokenTypes.NUMBER, COMMA, BashTokenTypes.NUMBER, _EXPR_ARITH);

    testTokenization("$((-1))", DOLLAR, EXPR_ARITH, ARITH_MINUS, BashTokenTypes.NUMBER, _EXPR_ARITH);

    testTokenization("$((--1))", DOLLAR, EXPR_ARITH, ARITH_MINUS, ARITH_MINUS, BashTokenTypes.NUMBER, _EXPR_ARITH);

    testTokenization("$((--a))", DOLLAR, EXPR_ARITH, ARITH_MINUS_MINUS, WORD, _EXPR_ARITH);

    testTokenization("$((- --a))", DOLLAR, EXPR_ARITH, ARITH_MINUS, WHITESPACE, ARITH_MINUS_MINUS, WORD, _EXPR_ARITH);

    testTokenization("$((-1 -1))", DOLLAR, EXPR_ARITH, ARITH_MINUS, BashTokenTypes.NUMBER, WHITESPACE, ARITH_MINUS, BashTokenTypes.NUMBER, _EXPR_ARITH);

    testTokenization("$((a & b))", DOLLAR, EXPR_ARITH, WORD, WHITESPACE, ARITH_BITWISE_AND, WHITESPACE, WORD, _EXPR_ARITH);

    testTokenization("$((a && b))", DOLLAR, EXPR_ARITH, WORD, WHITESPACE, AND_AND, WHITESPACE, WORD, _EXPR_ARITH);

    testTokenization("$((a || b))", DOLLAR, EXPR_ARITH, WORD, WHITESPACE, OR_OR, WHITESPACE, WORD, _EXPR_ARITH);

    testTokenization("$((!a))", DOLLAR, EXPR_ARITH, ARITH_NEGATE, WORD, _EXPR_ARITH);

    testTokenization("$((~a))", DOLLAR, EXPR_ARITH, ARITH_BITWISE_NEGATE, WORD, _EXPR_ARITH);

    testTokenization("$((a>>2))", DOLLAR, EXPR_ARITH, WORD, BashTokenTypes.SHIFT_RIGHT, BashTokenTypes.NUMBER, _EXPR_ARITH);

    testTokenization("$((a<<2))", DOLLAR, EXPR_ARITH, WORD, SHIFT_LEFT, BashTokenTypes.NUMBER, _EXPR_ARITH);

    testTokenization("$((a|2))", DOLLAR, EXPR_ARITH, WORD, PIPE, BashTokenTypes.NUMBER, _EXPR_ARITH);

    testTokenization("$((a&2))", DOLLAR, EXPR_ARITH, WORD, ARITH_BITWISE_AND, BashTokenTypes.NUMBER, _EXPR_ARITH);

    testTokenization("$((a^2))", DOLLAR, EXPR_ARITH, WORD, ARITH_BITWISE_XOR, BashTokenTypes.NUMBER, _EXPR_ARITH);

    testTokenization("$((a%2))", DOLLAR, EXPR_ARITH, WORD, MOD, BashTokenTypes.NUMBER, _EXPR_ARITH);

    testTokenization("$((a-2))", DOLLAR, EXPR_ARITH, WORD, ARITH_MINUS, BashTokenTypes.NUMBER, _EXPR_ARITH);

    testTokenization("$((a--))", DOLLAR, EXPR_ARITH, WORD, ARITH_MINUS_MINUS, _EXPR_ARITH);

    testTokenization("$((--a))", DOLLAR, EXPR_ARITH, ARITH_MINUS_MINUS, WORD, _EXPR_ARITH);

    testTokenization("$((a,2))", DOLLAR, EXPR_ARITH, WORD, COMMA, BashTokenTypes.NUMBER, _EXPR_ARITH);

    testTokenization("$((a>2))", DOLLAR, EXPR_ARITH, WORD, ARITH_GT, BashTokenTypes.NUMBER, _EXPR_ARITH);

    testTokenization("$((a > 2))", DOLLAR, EXPR_ARITH, WORD, WHITESPACE, ARITH_GT, WHITESPACE, BashTokenTypes.NUMBER, _EXPR_ARITH);

    testTokenization("$((a>=2))", DOLLAR, EXPR_ARITH, WORD, ARITH_GE, BashTokenTypes.NUMBER, _EXPR_ARITH);

    testTokenization("$((a<2))", DOLLAR, EXPR_ARITH, WORD, ARITH_LT, BashTokenTypes.NUMBER, _EXPR_ARITH);

    testTokenization("$((a<=2))", DOLLAR, EXPR_ARITH, WORD, ARITH_LE, BashTokenTypes.NUMBER, _EXPR_ARITH);

    testTokenization("$((1+-45))", DOLLAR, EXPR_ARITH, BashTokenTypes.NUMBER, ARITH_PLUS, ARITH_MINUS, BashTokenTypes.NUMBER, _EXPR_ARITH);

    testTokenization("$((1+(-45)))", DOLLAR, EXPR_ARITH, BashTokenTypes.NUMBER, ARITH_PLUS, LEFT_PAREN, ARITH_MINUS, BashTokenTypes.NUMBER, RIGHT_PAREN, _EXPR_ARITH);

    testTokenization("$((1+---45))", DOLLAR, EXPR_ARITH, BashTokenTypes.NUMBER, ARITH_PLUS, ARITH_MINUS, ARITH_MINUS, ARITH_MINUS, BashTokenTypes.NUMBER, _EXPR_ARITH);

    testTokenization("$(((1 << 10)))", DOLLAR, LEFT_PAREN, EXPR_ARITH, BashTokenTypes.NUMBER, WHITESPACE, SHIFT_LEFT, WHITESPACE, BashTokenTypes.NUMBER, _EXPR_ARITH, RIGHT_PAREN);

    testTokenization("$((1 < \"1\"))", DOLLAR, EXPR_ARITH, BashTokenTypes.NUMBER, WHITESPACE, ARITH_LT, WHITESPACE, STRING_BEGIN, STRING_CONTENT, STRING_END, _EXPR_ARITH);

    testTokenization("$((((1))))", DOLLAR, LEFT_PAREN, EXPR_ARITH, LEFT_PAREN, BashTokenTypes.NUMBER, RIGHT_PAREN, _EXPR_ARITH, RIGHT_PAREN);

    testTokenization("$((10#$x/10))", DOLLAR, EXPR_ARITH, BashTokenTypes.NUMBER, ARITH_BASE_CHAR, VARIABLE, DIV, BashTokenTypes.NUMBER, _EXPR_ARITH);
  }

  @Ignore
  @Test
  public void testLetExpressions() {
    //fixme unsure how the let expression should be tokenized. A solution might be to parse it as an lazy expression
    testTokenization("let a+=1", LET, WHITESPACE, ASSIGNMENT_WORD, ARITH_ASS_PLUS, BashTokenTypes.NUMBER);
    testTokenization("let", LET);
  }

  @Test
  public void testShebang() {
    testTokenization("#!", SHEBANG);

    testTokenization("#!/bin/bash", SHEBANG);

    testTokenization("#!/bin/bash\n", SHEBANG);

    testTokenization("#!/bin/bash\n\r", SHEBANG, BashTokenTypes.LINEFEED);

    testTokenization("\n#!/bin/bash", BashTokenTypes.LINEFEED, SHEBANG);
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
    testTokenization("\"$(1)\"", STRING_BEGIN, DOLLAR, LEFT_PAREN, INT, RIGHT_PAREN, STRING_END);
    testTokenization("\"$((1))\"", STRING_BEGIN, DOLLAR, EXPR_ARITH, BashTokenTypes.NUMBER, _EXPR_ARITH, STRING_END);

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
    testTokenization("[ \\${a} ]", EXPR_CONDITIONAL_LEFT, WORD, LEFT_CURLY, WORD, RIGHT_CURLY, EXPR_CONDITIONAL_RIGHT);
    testTokenization("[  ]", EXPR_CONDITIONAL_LEFT, EXPR_CONDITIONAL_RIGHT);
    testTokenization("[ ]", EXPR_CONDITIONAL_LEFT, EXPR_CONDITIONAL_RIGHT);
    testTokenization("[ a  ]", EXPR_CONDITIONAL_LEFT, WORD, WHITESPACE, EXPR_CONDITIONAL_RIGHT);
    testTokenization("[ a | b ]", EXPR_CONDITIONAL_LEFT, WORD, WHITESPACE, PIPE, WHITESPACE, WORD, EXPR_CONDITIONAL_RIGHT);
    testTokenization("[[ a || b ]]", LEFT_DOUBLE_BRACKET, WORD, WHITESPACE, OR_OR, WHITESPACE, WORD, RIGHT_DOUBLE_BRACKET);
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
        IF, WHITESPACE, EXPR_CONDITIONAL_LEFT, COND_OP, WHITESPACE, STRING_BEGIN, STRING_CONTENT, STRING_END, EXPR_CONDITIONAL_RIGHT, SEMI,
        WHITESPACE, THEN, WHITESPACE, WORD, SEMI, WHITESPACE, FI, SEMI);

    testTokenization("$((1+2))", DOLLAR, EXPR_ARITH, BashTokenTypes.NUMBER, ARITH_PLUS, BashTokenTypes.NUMBER, _EXPR_ARITH);
    testTokenization("((i=$(echo 1)))", EXPR_ARITH, ASSIGNMENT_WORD, EQ, DOLLAR, LEFT_PAREN, WORD, WHITESPACE, INT, RIGHT_PAREN, _EXPR_ARITH);
    testTokenization("((i=$((1 + 9))))",
        EXPR_ARITH, ASSIGNMENT_WORD, EQ, DOLLAR, EXPR_ARITH, BashTokenTypes.NUMBER, WHITESPACE,
        ARITH_PLUS, WHITESPACE, BashTokenTypes.NUMBER, _EXPR_ARITH, _EXPR_ARITH);

    testTokenization("((1 == 1 ? 0 : 0))",
        EXPR_ARITH, BashTokenTypes.NUMBER, WHITESPACE, ARITH_EQ, WHITESPACE, BashTokenTypes.NUMBER, WHITESPACE, ARITH_QMARK,
        WHITESPACE, BashTokenTypes.NUMBER, WHITESPACE, ARITH_COLON, WHITESPACE, BashTokenTypes.NUMBER, _EXPR_ARITH);

    testTokenization("a=(\na #this is a comment\nb)", ASSIGNMENT_WORD, EQ, LEFT_PAREN, BashTokenTypes.LINEFEED, WORD, WHITESPACE, COMMENT, BashTokenTypes.LINEFEED, WORD, RIGHT_PAREN);
  }

  @Test
  public void testSubshell() {
    testTokenization("$(echo \"$1\")",
        DOLLAR, LEFT_PAREN, WORD, WHITESPACE, STRING_BEGIN, VARIABLE, STRING_END, RIGHT_PAREN);
    testTokenization("$(($(echo \"$1\")))",
        DOLLAR, EXPR_ARITH, DOLLAR, LEFT_PAREN, WORD, WHITESPACE, STRING_BEGIN, VARIABLE, STRING_END, RIGHT_PAREN, _EXPR_ARITH);
    testTokenization("`for d in`",
        BACKQUOTE, FOR, WHITESPACE, WORD, WHITESPACE, WORD, BACKQUOTE);
    testTokenization("[ \"`dd`\" ]",
        EXPR_CONDITIONAL_LEFT, STRING_BEGIN, BACKQUOTE, WORD, BACKQUOTE, STRING_END, EXPR_CONDITIONAL_RIGHT);
  }

  @Test
  public void testNumber() {
    testTokenization("123", INT);
    testTokenization("$((123))", DOLLAR, EXPR_ARITH, BashTokenTypes.NUMBER, _EXPR_ARITH);

    testTokenization("123 456", INT, WHITESPACE, INT);
    testTokenization("$((123 234))", DOLLAR, EXPR_ARITH, BashTokenTypes.NUMBER, WHITESPACE, BashTokenTypes.NUMBER, _EXPR_ARITH);
  }

  @Test
  public void testFunction() {
    testTokenization("function", FUNCTION);
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
    testTokenization(">1", GREATER_THAN, INT);
    testTokenization("> 1", GREATER_THAN, WHITESPACE, INT);
    testTokenization(">&1", GREATER_THAN, FILEDESCRIPTOR);

    testTokenization("<&-", LESS_THAN, FILEDESCRIPTOR);
    testTokenization(">&-", GREATER_THAN, FILEDESCRIPTOR);
    testTokenization("1>&-", INT, GREATER_THAN, FILEDESCRIPTOR);
    testTokenization("1>&-", INT, GREATER_THAN, FILEDESCRIPTOR);

    testTokenization("3>&9", INT, GREATER_THAN, FILEDESCRIPTOR);
    testTokenization("3>&10", INT, GREATER_THAN, FILEDESCRIPTOR);
    testTokenization("10>&10", INT, GREATER_THAN, FILEDESCRIPTOR);
    testTokenization("10>&a123", INT, REDIRECT_GREATER_AMP, WORD);
    testTokenization("10>& a123", INT, REDIRECT_GREATER_AMP, WHITESPACE, WORD);

    testTokenization("$(a >&1)", DOLLAR, LEFT_PAREN, WORD, WHITESPACE, GREATER_THAN, FILEDESCRIPTOR, RIGHT_PAREN);
  }

  @Test
  public void testConditional() {
    testTokenization("[ 1 = \"$backgrounded\" ]", EXPR_CONDITIONAL_LEFT, WORD, WHITESPACE, COND_OP, WHITESPACE, STRING_BEGIN, VARIABLE, STRING_END, EXPR_CONDITIONAL_RIGHT);

    testTokenization("[ 1 == 1 ]", EXPR_CONDITIONAL_LEFT, WORD, WHITESPACE, COND_OP_EQ_EQ, WHITESPACE, WORD, EXPR_CONDITIONAL_RIGHT);

    testTokenization("[ 1 =~ 1 ]", EXPR_CONDITIONAL_LEFT, WORD, WHITESPACE, COND_OP_REGEX, WHITESPACE, WORD, EXPR_CONDITIONAL_RIGHT);
  }

  @Test
  public void testBracket() {
    testTokenization("[[ -f test.txt ]]", LEFT_DOUBLE_BRACKET, COND_OP, WHITESPACE, WORD, RIGHT_DOUBLE_BRACKET);
    testTokenization(" ]]", WHITESPACE, WORD);
    testTokenization("  ]]", WHITESPACE, WHITESPACE, WORD);
    testTokenization("[[  -f test.txt   ]]", LEFT_DOUBLE_BRACKET, WHITESPACE, COND_OP, WHITESPACE, WORD, WHITESPACE, WHITESPACE, RIGHT_DOUBLE_BRACKET);
    testTokenization("[[ !(a) ]]", LEFT_DOUBLE_BRACKET, COND_OP_NOT, LEFT_PAREN, WORD, RIGHT_PAREN, RIGHT_DOUBLE_BRACKET);

    testTokenization("[[ a && b ]]", LEFT_DOUBLE_BRACKET, WORD, WHITESPACE, AND_AND, WHITESPACE, WORD, RIGHT_DOUBLE_BRACKET);
    testTokenization("[[ a || b ]]", LEFT_DOUBLE_BRACKET, WORD, WHITESPACE, OR_OR, WHITESPACE, WORD, RIGHT_DOUBLE_BRACKET);

    testTokenization("[[ -z \"\" ]]", LEFT_DOUBLE_BRACKET, COND_OP, WHITESPACE, STRING_BEGIN, STRING_END, RIGHT_DOUBLE_BRACKET);

    testTokenization("[[ a == b ]]", LEFT_DOUBLE_BRACKET, WORD, WHITESPACE, COND_OP_EQ_EQ, WHITESPACE, WORD, RIGHT_DOUBLE_BRACKET);
    testTokenization("[[ a =~ b ]]", LEFT_DOUBLE_BRACKET, WORD, WHITESPACE, COND_OP_REGEX, WHITESPACE, WORD, RIGHT_DOUBLE_BRACKET);
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
        FUNCTION, WHITESPACE, WORD, BashTokenTypes.LINEFEED, DOLLAR, LEFT_CURLY, WORD, PARAM_EXPANSION_OP_SLASH, RIGHT_CURLY, BashTokenTypes.LINEFEED,
        DOLLAR, LEFT_CURLY, WORD, PARAM_EXPANSION_OP_SLASH, RIGHT_CURLY, BashTokenTypes.LINEFEED,
        RIGHT_CURLY);
  }

  @Test
  public void testWeirdStuff1() {
    testTokenization(": > a", WORD, WHITESPACE, GREATER_THAN, WHITESPACE, WORD);
    testTokenization("^", WORD);
    testTokenization("$!", VARIABLE);
    testTokenization("!", BANG);
    testTokenization("+m", WORD);
    testTokenization("\\#", WORD);
    testTokenization("a,b", WORD);
    testTokenization("~", WORD);
    testTokenization("a~", WORD);
    testTokenization("\\$", WORD);
    testTokenization("a[1]=2", ASSIGNMENT_WORD, LEFT_SQUARE, BashTokenTypes.NUMBER, RIGHT_SQUARE, EQ, INT);
    testTokenization("a[1]='2'", ASSIGNMENT_WORD, LEFT_SQUARE, BashTokenTypes.NUMBER, RIGHT_SQUARE, EQ, STRING2);
    testTokenization("a[1+2]='2'", ASSIGNMENT_WORD, LEFT_SQUARE, BashTokenTypes.NUMBER, ARITH_PLUS, BashTokenTypes.NUMBER, RIGHT_SQUARE, EQ, STRING2);
    testTokenization("esac;", WORD, SEMI);

    //"$(echo "123")"
    testTokenization("\"$(echo 123)\"", STRING_BEGIN, DOLLAR, LEFT_PAREN, WORD, WHITESPACE, INT, RIGHT_PAREN, STRING_END);
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
        "done;", FOR, WHITESPACE, BashTokenTypes.CASE, WHITESPACE, WORD, WHITESPACE, WORD, SEMI, WHITESPACE, DO, BashTokenTypes.LINEFEED, WORD, BashTokenTypes.LINEFEED, DONE, SEMI);
  }

  @Test
  public void testCaseWhitespacePattern() {
    testTokenization("case x in\n" +
        "a\\ b)\n" +
        ";;\n" +
        "esac", BashTokenTypes.CASE, WHITESPACE, WORD, WHITESPACE, WORD, BashTokenTypes.LINEFEED, WORD, RIGHT_PAREN, BashTokenTypes.LINEFEED, CASE_END, BashTokenTypes.LINEFEED, ESAC);

  }

  @Test
  public void testNestedCase() {
    testTokenization("case x in x) ;; esac",
        BashTokenTypes.CASE, WHITESPACE, WORD, WHITESPACE, WORD, WHITESPACE, WORD, RIGHT_PAREN, WHITESPACE, CASE_END, WHITESPACE, ESAC);

    testTokenization("$(case x in x) ;; esac)",
        DOLLAR, LEFT_PAREN, BashTokenTypes.CASE, WHITESPACE, WORD, WHITESPACE, WORD, WHITESPACE, WORD, RIGHT_PAREN, WHITESPACE, CASE_END, WHITESPACE, ESAC, RIGHT_PAREN);
    testTokenization("(case x in x) ;; esac)",
        LEFT_PAREN, BashTokenTypes.CASE, WHITESPACE, WORD, WHITESPACE, WORD, WHITESPACE, WORD, RIGHT_PAREN, WHITESPACE, CASE_END, WHITESPACE, ESAC, RIGHT_PAREN);

    testTokenization("`case x in x) ;; esac `",
        BACKQUOTE, BashTokenTypes.CASE, WHITESPACE, WORD, WHITESPACE, WORD, WHITESPACE, WORD, RIGHT_PAREN, WHITESPACE, CASE_END, WHITESPACE, ESAC, WHITESPACE, BACKQUOTE);

    testTokenization("case x in esac",
        BashTokenTypes.CASE, WHITESPACE, WORD, WHITESPACE, WORD, WHITESPACE, ESAC);

    testTokenization("`case x in esac`;",
        BACKQUOTE, BashTokenTypes.CASE, WHITESPACE, WORD, WHITESPACE, WORD, WHITESPACE, ESAC, BACKQUOTE, SEMI);

    testTokenization("case x in\n" +
            "a\\ b)\n" +
            "x=`case x in x) echo;; esac`\n" +
            ";;\n" +
            "esac",
        BashTokenTypes.CASE, WHITESPACE, WORD, WHITESPACE, WORD, BashTokenTypes.LINEFEED,
        WORD, RIGHT_PAREN, BashTokenTypes.LINEFEED,
        ASSIGNMENT_WORD, EQ, BACKQUOTE, BashTokenTypes.CASE, WHITESPACE, WORD, WHITESPACE, WORD, WHITESPACE, WORD, RIGHT_PAREN, WHITESPACE, WORD, CASE_END, WHITESPACE, ESAC, BACKQUOTE, BashTokenTypes.LINEFEED,
        CASE_END, BashTokenTypes.LINEFEED, ESAC);

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
        BashTokenTypes.CASE, WHITESPACE, WORD, WHITESPACE, WORD, WHITESPACE, WORD, RIGHT_PAREN,
        CASE_END, WHITESPACE, ESAC);

    testTokenization("case a in a/ui);; esac",
        BashTokenTypes.CASE, WHITESPACE, WORD, WHITESPACE, WORD, WHITESPACE, WORD, RIGHT_PAREN,
        CASE_END, WHITESPACE, ESAC);

    testTokenization("case a in a#);; esac",
        BashTokenTypes.CASE, WHITESPACE, WORD, WHITESPACE, WORD, WHITESPACE, WORD, RIGHT_PAREN,
        CASE_END, WHITESPACE, ESAC);

    testTokenization("case a in\n  a#);; esac",
        BashTokenTypes.CASE, WHITESPACE, WORD, WHITESPACE, WORD,
        BashTokenTypes.LINEFEED, WHITESPACE, WHITESPACE,
        WORD, RIGHT_PAREN, CASE_END, WHITESPACE, ESAC);

    testTokenization("case a in \"a b\") echo a;; esac",
        BashTokenTypes.CASE, WHITESPACE, WORD, WHITESPACE, WORD, WHITESPACE, STRING_BEGIN, STRING_CONTENT, STRING_END, RIGHT_PAREN,
        WHITESPACE, WORD, WHITESPACE, WORD, CASE_END, WHITESPACE, ESAC);

    //v3 vs. v4 changes in end marker
    testTokenization(BashVersion.V4, "case a in a);;& esac",
        BashTokenTypes.CASE, WHITESPACE, WORD, WHITESPACE, WORD,
        WHITESPACE, WORD, RIGHT_PAREN, CASE_END, WHITESPACE, ESAC);
    testTokenization(BashVersion.V3, "case a in a);;& esac",
        BashTokenTypes.CASE, WHITESPACE, WORD, WHITESPACE, WORD,
        WHITESPACE, WORD, RIGHT_PAREN, CASE_END, AMP, WHITESPACE, ESAC);

    //v3 vs. v4 changes in new end marker
    testTokenization(BashVersion.V4, "case a in a);& esac",
        BashTokenTypes.CASE, WHITESPACE, WORD, WHITESPACE, WORD,
        WHITESPACE, WORD, RIGHT_PAREN, CASE_END, WHITESPACE, ESAC);
    testTokenization(BashVersion.V3, "case a in a);& esac",
        BashTokenTypes.CASE, WHITESPACE, WORD, WHITESPACE, WORD,
        WHITESPACE, WORD, RIGHT_PAREN, SEMI, AMP, WHITESPACE, ESAC);

    testTokenization("case a in a=a) echo a;; esac;",
        BashTokenTypes.CASE, WHITESPACE, WORD, WHITESPACE, WORD, WHITESPACE, WORD, RIGHT_PAREN,
        WHITESPACE, WORD, WHITESPACE, WORD, CASE_END, WHITESPACE, ESAC, SEMI);

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
        WORD, WHITESPACE, EXPR_CONDITIONAL_LEFT, STRING_BEGIN, STRING_CONTENT, STRING_END, EXPR_CONDITIONAL_RIGHT);

    testTokenization("eval \"echo $\"",
        WORD, WHITESPACE, STRING_BEGIN, STRING_CONTENT, STRING_END);

    testTokenization("for f in a; do eval [ \"a\" ]; done",
        FOR, WHITESPACE, WORD, WHITESPACE, WORD, WHITESPACE, WORD, SEMI,
        WHITESPACE, DO, WHITESPACE,
        WORD, WHITESPACE, EXPR_CONDITIONAL_LEFT, STRING_BEGIN, STRING_CONTENT, STRING_END, EXPR_CONDITIONAL_RIGHT,
        SEMI, WHITESPACE, DONE);

    testTokenization("case a in a) echo [ \"a\" ];; esac",
        BashTokenTypes.CASE, WHITESPACE, WORD, WHITESPACE, WORD, WHITESPACE, WORD,
        RIGHT_PAREN, WHITESPACE, WORD, WHITESPACE, EXPR_CONDITIONAL_LEFT, STRING_BEGIN, STRING_CONTENT, STRING_END, EXPR_CONDITIONAL_RIGHT, CASE_END, WHITESPACE, ESAC);
  }

  @Test
  public void testNestedStatements() {
    testTokenization("case a in a) for do done ;; esac",
        BashTokenTypes.CASE, WHITESPACE, WORD, WHITESPACE, WORD, WHITESPACE, WORD,
        RIGHT_PAREN, WHITESPACE, FOR, WHITESPACE, DO, WHITESPACE, DONE, WHITESPACE, CASE_END, WHITESPACE,
        ESAC);

    testTokenization("case a in a) in ;; esac",
        BashTokenTypes.CASE, WHITESPACE, WORD, WHITESPACE, WORD, WHITESPACE, WORD,
        RIGHT_PAREN, WHITESPACE, WORD, WHITESPACE, CASE_END, WHITESPACE,
        ESAC);

    testTokenization("if; a; then\nb #123\nfi",
        IF, SEMI, WHITESPACE, WORD, SEMI, WHITESPACE, THEN, BashTokenTypes.LINEFEED,
        WORD, WHITESPACE, COMMENT, BashTokenTypes.LINEFEED, FI);

    testTokenization("for ((a=1;;))",
        FOR, WHITESPACE, EXPR_ARITH, ASSIGNMENT_WORD, EQ, BashTokenTypes.NUMBER, SEMI,
        SEMI, _EXPR_ARITH);

    testTokenization("for ((a=1;a;))",
        FOR, WHITESPACE, EXPR_ARITH, ASSIGNMENT_WORD, EQ, BashTokenTypes.NUMBER, SEMI,
        WORD, SEMI, _EXPR_ARITH);

    testTokenization("for ((a=1;a<2;a=a+1))",
        FOR, WHITESPACE, EXPR_ARITH, ASSIGNMENT_WORD, EQ, BashTokenTypes.NUMBER, SEMI,
        WORD, ARITH_LT, BashTokenTypes.NUMBER, SEMI, ASSIGNMENT_WORD, EQ, WORD, ARITH_PLUS, BashTokenTypes.NUMBER, _EXPR_ARITH);

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
        DOLLAR, LEFT_CURLY, WORD, LEFT_SQUARE, WORD, ARITH_PLUS, WORD, ARITH_PLUS, BashTokenTypes.NUMBER, RIGHT_SQUARE,
        PARAM_EXPANSION_OP_HASH, LEFT_SQUARE, WORD, PARAM_EXPANSION_OP_MINUS, WORD, RIGHT_SQUARE,
        LEFT_SQUARE, WORD, RIGHT_SQUARE,
        RIGHT_CURLY);

    testTokenization("${#a[1]}", DOLLAR, LEFT_CURLY, PARAM_EXPANSION_OP_HASH, WORD, LEFT_SQUARE, BashTokenTypes.NUMBER, RIGHT_SQUARE, RIGHT_CURLY);

    testTokenization("${a-`$[1]`}", DOLLAR, LEFT_CURLY, WORD, PARAM_EXPANSION_OP_MINUS, BACKQUOTE, DOLLAR, EXPR_ARITH_SQUARE, BashTokenTypes.NUMBER, _EXPR_ARITH_SQUARE, BACKQUOTE, RIGHT_CURLY);
  }

  @Test
  public void testArithmeticLiterals() {
    testTokenization("$((123))", DOLLAR, EXPR_ARITH, BashTokenTypes.NUMBER, _EXPR_ARITH);

    testTokenization("$((0x123))", DOLLAR, EXPR_ARITH, ARITH_HEX_NUMBER, _EXPR_ARITH);
    testTokenization("$((0xA))", DOLLAR, EXPR_ARITH, ARITH_HEX_NUMBER, _EXPR_ARITH);
    testTokenization("$((0xAf))", DOLLAR, EXPR_ARITH, ARITH_HEX_NUMBER, _EXPR_ARITH);
    testTokenization("$((-0xAf))", DOLLAR, EXPR_ARITH, ARITH_MINUS, ARITH_HEX_NUMBER, _EXPR_ARITH);
    testTokenization("$((--0xAf))", DOLLAR, EXPR_ARITH, ARITH_MINUS, ARITH_MINUS, ARITH_HEX_NUMBER, _EXPR_ARITH);
    testTokenization("$((+0xAf))", DOLLAR, EXPR_ARITH, ARITH_PLUS, ARITH_HEX_NUMBER, _EXPR_ARITH);

    testTokenization("$((10#1))", DOLLAR, EXPR_ARITH, BashTokenTypes.NUMBER, ARITH_BASE_CHAR, BashTokenTypes.NUMBER, _EXPR_ARITH);
    testTokenization("$((10#100))", DOLLAR, EXPR_ARITH, BashTokenTypes.NUMBER, ARITH_BASE_CHAR, BashTokenTypes.NUMBER, _EXPR_ARITH);
    testTokenization("$((-10#100))", DOLLAR, EXPR_ARITH, ARITH_MINUS, BashTokenTypes.NUMBER, ARITH_BASE_CHAR, BashTokenTypes.NUMBER, _EXPR_ARITH);
    testTokenization("$((+10#100))", DOLLAR, EXPR_ARITH, ARITH_PLUS, BashTokenTypes.NUMBER, ARITH_BASE_CHAR, BashTokenTypes.NUMBER, _EXPR_ARITH);

    testTokenization("$((0123))", DOLLAR, EXPR_ARITH, ARITH_OCTAL_NUMBER, _EXPR_ARITH);
    testTokenization("$((-0123))", DOLLAR, EXPR_ARITH, ARITH_MINUS, ARITH_OCTAL_NUMBER, _EXPR_ARITH);

    //also afe is not valid here we expect it to lex because we check the base in
    //an inspection
    testTokenization("$((10#100afe))", DOLLAR, EXPR_ARITH, BashTokenTypes.NUMBER, ARITH_BASE_CHAR, BashTokenTypes.NUMBER, WORD, _EXPR_ARITH);

    testTokenization("$((12#D))", DOLLAR, EXPR_ARITH, BashTokenTypes.NUMBER, ARITH_BASE_CHAR, WORD, _EXPR_ARITH);

    testTokenization("$((35#abcdefghijkl))", DOLLAR, EXPR_ARITH, BashTokenTypes.NUMBER, ARITH_BASE_CHAR, WORD, _EXPR_ARITH);
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
    testTokenization("cat <<END\nEND", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, BashTokenTypes.LINEFEED, HEREDOC_MARKER_END);
    testTokenization("cat <<END;\nEND", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, SEMI, BashTokenTypes.LINEFEED, HEREDOC_MARKER_END);
    testTokenization("cat <<END&& test\nEND", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, AND_AND, WHITESPACE, WORD, BashTokenTypes.LINEFEED, HEREDOC_MARKER_END);
    testTokenization("cat <<END && test\nEND", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, WHITESPACE, AND_AND, WHITESPACE, WORD, BashTokenTypes.LINEFEED, HEREDOC_MARKER_END);
    testTokenization("cat <<END || test\nEND", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, WHITESPACE, OR_OR, WHITESPACE, WORD, BashTokenTypes.LINEFEED, HEREDOC_MARKER_END);

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
    testTokenization("cat <<END\n", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, BashTokenTypes.LINEFEED);
    testTokenization("cat <<END\nABC\n", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, BashTokenTypes.LINEFEED, HEREDOC_CONTENT);
    testTokenization("cat <<END\nABC\n\n", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, BashTokenTypes.LINEFEED, HEREDOC_CONTENT);
    testTokenization("cat <<END\nABC", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, BashTokenTypes.LINEFEED, HEREDOC_CONTENT);

    testTokenization("cat <<END\nABC\nDEF\nEND\n", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, BashTokenTypes.LINEFEED, HEREDOC_CONTENT, HEREDOC_MARKER_END, BashTokenTypes.LINEFEED);
    testTokenization("cat << END\nABC\nDEF\nEND\n", WORD, WHITESPACE, HEREDOC_MARKER_TAG, WHITESPACE, HEREDOC_MARKER_START, BashTokenTypes.LINEFEED, HEREDOC_CONTENT, HEREDOC_MARKER_END, BashTokenTypes.LINEFEED);

    testTokenization("cat <<-END\nABC\nDEF\nEND\n", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, BashTokenTypes.LINEFEED, HEREDOC_CONTENT, HEREDOC_MARKER_IGNORING_TABS_END, BashTokenTypes.LINEFEED);
    testTokenization("cat <<- END\nABC\nDEF\nEND\n", WORD, WHITESPACE, HEREDOC_MARKER_TAG, WHITESPACE, HEREDOC_MARKER_START, BashTokenTypes.LINEFEED, HEREDOC_CONTENT, HEREDOC_MARKER_IGNORING_TABS_END, BashTokenTypes.LINEFEED);

    testTokenization("cat <<END\nABC\nDEF\nEND", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, BashTokenTypes.LINEFEED, HEREDOC_CONTENT, HEREDOC_MARKER_END);

    testTokenization("cat <<END\nABC\nDEF\n\n\nXYZ DEF\nEND", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, BashTokenTypes.LINEFEED, HEREDOC_CONTENT, HEREDOC_MARKER_END);

    testTokenization("cat <<!\n!", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, BashTokenTypes.LINEFEED, HEREDOC_MARKER_END);

    testTokenization("{\n" +
        "cat <<EOF\n" +
        "test\n" +
        "EOF\n" +
        "}", LEFT_CURLY, BashTokenTypes.LINEFEED, WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, BashTokenTypes.LINEFEED, HEREDOC_CONTENT, HEREDOC_MARKER_END, BashTokenTypes.LINEFEED, RIGHT_CURLY);

    testTokenization("cat <<EOF\n" +
        "$test\n" +
        "EOF", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, BashTokenTypes.LINEFEED, VARIABLE, HEREDOC_CONTENT, HEREDOC_MARKER_END);

    testTokenization("{\n" +
        "cat <<EOF\n" +
        "$(test)\n" +
        "EOF\n" +
        "}", LEFT_CURLY, BashTokenTypes.LINEFEED, WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, BashTokenTypes.LINEFEED, DOLLAR, LEFT_PAREN, WORD, RIGHT_PAREN, HEREDOC_CONTENT, HEREDOC_MARKER_END, BashTokenTypes.LINEFEED, RIGHT_CURLY);

    testTokenization("if test\n" +
            "cat <<EOF\n" +
            "EOF\n" +
            "test\n" +
            "fi",
        IF, WHITESPACE, WORD, BashTokenTypes.LINEFEED,
        WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, BashTokenTypes.LINEFEED,
        HEREDOC_MARKER_END, BashTokenTypes.LINEFEED,
        WORD, BashTokenTypes.LINEFEED,
        FI);

    testTokenization("cat <<X <<\n", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, WHITESPACE, HEREDOC_MARKER_TAG, BashTokenTypes.LINEFEED);

    testTokenization("cat <<$\n$", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, BashTokenTypes.LINEFEED, HEREDOC_MARKER_END);
    testTokenization("cat <<$_X\n$_X", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, BashTokenTypes.LINEFEED, HEREDOC_MARKER_END);

    //tab prefixed end marker is valid if started by <<-
    //no tab
    testTokenization("cat <<-EOF\nEOF", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, BashTokenTypes.LINEFEED, HEREDOC_MARKER_IGNORING_TABS_END);
    testTokenization("cat <<- EOF\nEOF", WORD, WHITESPACE, HEREDOC_MARKER_TAG, WHITESPACE, HEREDOC_MARKER_START, BashTokenTypes.LINEFEED, HEREDOC_MARKER_IGNORING_TABS_END);
    //with tab
    testTokenization("cat <<-EOF\n\tEOF", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, BashTokenTypes.LINEFEED, HEREDOC_MARKER_IGNORING_TABS_END);
    testTokenization("cat <<- EOF\n\tEOF", WORD, WHITESPACE, HEREDOC_MARKER_TAG, WHITESPACE, HEREDOC_MARKER_START, BashTokenTypes.LINEFEED, HEREDOC_MARKER_IGNORING_TABS_END);
    //with tab but without <<-
    testTokenization("cat <<EOF\n\tEOF", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, BashTokenTypes.LINEFEED, HEREDOC_CONTENT);

    // subshell expression
    testTokenization("{\n" +
            "<<EOF <<EOF2\n" +
            "EOF\n" +
            "$(a)\n" +
            "EOF2", LEFT_CURLY, BashTokenTypes.LINEFEED, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, BashTokenTypes.LINEFEED,
        HEREDOC_MARKER_END, BashTokenTypes.LINEFEED,
        DOLLAR, LEFT_PAREN, WORD, RIGHT_PAREN, HEREDOC_CONTENT,
        HEREDOC_MARKER_END
    );

    //normal arithmetic expression
    testTokenization("{\n" +
            "<<EOF <<EOF2\n" +
            "EOF\n" +
            "$((a))\n" +
            "EOF2", LEFT_CURLY, BashTokenTypes.LINEFEED, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, BashTokenTypes.LINEFEED,
        HEREDOC_MARKER_END, BashTokenTypes.LINEFEED,
        DOLLAR, EXPR_ARITH, WORD, _EXPR_ARITH, HEREDOC_CONTENT,
        HEREDOC_MARKER_END
    );

    //square arithmetic expression
    testTokenization("{\n" +
            "<<EOF <<EOF2\n" +
            "EOF\n" +
            "$[a]\n" +
            "EOF2", LEFT_CURLY, BashTokenTypes.LINEFEED, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, BashTokenTypes.LINEFEED,
        HEREDOC_MARKER_END, BashTokenTypes.LINEFEED,
        DOLLAR, EXPR_ARITH_SQUARE, WORD, _EXPR_ARITH_SQUARE, HEREDOC_CONTENT,
        HEREDOC_MARKER_END
    );
  }

  @Test
  public void testMultilineHeredoc() {
    //multiple heredocs in one command line
    testTokenization("cat <<END <<END2\nABC\nEND\nABC\nEND2\n", WORD, WHITESPACE, HEREDOC_MARKER_TAG,
        HEREDOC_MARKER_START, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, BashTokenTypes.LINEFEED,
        HEREDOC_CONTENT, HEREDOC_MARKER_END, BashTokenTypes.LINEFEED, HEREDOC_CONTENT, HEREDOC_MARKER_END, BashTokenTypes.LINEFEED);
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
    testTokenization("read 'test' a[0]", WORD, WHITESPACE, STRING2, WHITESPACE, ASSIGNMENT_WORD, LEFT_SQUARE, BashTokenTypes.NUMBER, RIGHT_SQUARE);
  }

  @Test
  public void testIssue199() {
    testTokenization("$( ((count != 1)) && echo)", DOLLAR, LEFT_PAREN, WHITESPACE, EXPR_ARITH, WORD, WHITESPACE, ARITH_NE, WHITESPACE, BashTokenTypes.NUMBER, _EXPR_ARITH, WHITESPACE, AND_AND, WHITESPACE, WORD, RIGHT_PAREN);
    testTokenization("$(((count != 1)) && echo)", DOLLAR, LEFT_PAREN, EXPR_ARITH, WORD, WHITESPACE, ARITH_NE, WHITESPACE, BashTokenTypes.NUMBER, _EXPR_ARITH, WHITESPACE, AND_AND, WHITESPACE, WORD, RIGHT_PAREN);
    //limitation of the Bash lexer: no look-ahead to the end of an expression
    //Bash parses this (probably) as an arithmetic expression with a parenthesis inside
    //BashSupport doesn't
    testTokenization("(((1==1)))", LEFT_PAREN, EXPR_ARITH, BashTokenTypes.NUMBER, ARITH_EQ, BashTokenTypes.NUMBER, _EXPR_ARITH, RIGHT_PAREN);

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
        "EOF", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, BashTokenTypes.LINEFEED, HEREDOC_CONTENT, HEREDOC_MARKER_END);

    //heredoc with evaluation
    testTokenization("cat <<EOF\n" +
        "    echo ${counter}\n" +
        "EOF", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, BashTokenTypes.LINEFEED, HEREDOC_CONTENT, DOLLAR, LEFT_CURLY, WORD, RIGHT_CURLY, HEREDOC_CONTENT, HEREDOC_MARKER_END);

    //heredoc with escaped variable
    testTokenization("cat <<EOF\n" +
        "\\$counter\n" +
        "EOF", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, BashTokenTypes.LINEFEED, HEREDOC_CONTENT, HEREDOC_MARKER_END);
    testTokenization("cat <<EOF\n" +
        "echo \\$counter\n" +
        "EOF", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, BashTokenTypes.LINEFEED, HEREDOC_CONTENT, HEREDOC_MARKER_END);
    testTokenization("cat <<EOF\n" +
        "echo \\\n" +
        "EOF", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, BashTokenTypes.LINEFEED, HEREDOC_CONTENT, HEREDOC_MARKER_END);
  }

  @Test
  public void testIssue272() {
    testTokenization("${#array_var[@]}", DOLLAR, LEFT_CURLY, PARAM_EXPANSION_OP_HASH, WORD, LEFT_SQUARE, PARAM_EXPANSION_OP_AT, RIGHT_SQUARE, RIGHT_CURLY);
  }

  @Test
  public void testIssue300() {
    testTokenization("case x in\nabc${a}) ;; esac",
        BashTokenTypes.CASE, WHITESPACE, WORD, WHITESPACE, WORD, BashTokenTypes.LINEFEED, WORD, DOLLAR, LEFT_CURLY, WORD, RIGHT_CURLY, RIGHT_PAREN, WHITESPACE, CASE_END, WHITESPACE, ESAC);
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
    testTokenization("[[ $# == \"x\" ]]", LEFT_DOUBLE_BRACKET, VARIABLE, WHITESPACE, COND_OP_EQ_EQ, WHITESPACE, STRING_BEGIN, STRING_CONTENT, STRING_END, RIGHT_DOUBLE_BRACKET);

    testTokenization("[[ ( $# == \"x\" ) ]]", LEFT_DOUBLE_BRACKET, LEFT_PAREN, WHITESPACE, VARIABLE, WHITESPACE, COND_OP_EQ_EQ, WHITESPACE, STRING_BEGIN, STRING_CONTENT, STRING_END, WHITESPACE, RIGHT_PAREN, RIGHT_DOUBLE_BRACKET);

    testTokenization("[[ ( $# == \"x\" ) || -f \"x\" || 123==123 ]]", LEFT_DOUBLE_BRACKET, LEFT_PAREN, WHITESPACE, VARIABLE, WHITESPACE, COND_OP_EQ_EQ, WHITESPACE, STRING_BEGIN, STRING_CONTENT, STRING_END, WHITESPACE, RIGHT_PAREN, WHITESPACE, OR_OR, WHITESPACE, COND_OP, WHITESPACE, STRING_BEGIN, STRING_CONTENT, STRING_END, WHITESPACE, OR_OR, WHITESPACE, WORD, COND_OP_EQ_EQ, WORD, RIGHT_DOUBLE_BRACKET);
  }

  @Test
  public void testIssue89() {
    testTokenization("function a {\n}function", FUNCTION, WHITESPACE, WORD, WHITESPACE, LEFT_CURLY, BashTokenTypes.LINEFEED, RIGHT_CURLY, FUNCTION);

  }

  @Test
  public void testIssue320() {
    testTokenization("(( a[0] ))", EXPR_ARITH, WHITESPACE, ASSIGNMENT_WORD, LEFT_SQUARE, BashTokenTypes.NUMBER, RIGHT_SQUARE, WHITESPACE, _EXPR_ARITH);
    testTokenization("(( A[0] ))", EXPR_ARITH, WHITESPACE, ASSIGNMENT_WORD, LEFT_SQUARE, BashTokenTypes.NUMBER, RIGHT_SQUARE, WHITESPACE, _EXPR_ARITH);

    testTokenization("(( a[0x0] ))", EXPR_ARITH, WHITESPACE, ASSIGNMENT_WORD, LEFT_SQUARE, ARITH_HEX_NUMBER, RIGHT_SQUARE, WHITESPACE, _EXPR_ARITH);
    testTokenization("(( A[0x0] ))", EXPR_ARITH, WHITESPACE, ASSIGNMENT_WORD, LEFT_SQUARE, ARITH_HEX_NUMBER, RIGHT_SQUARE, WHITESPACE, _EXPR_ARITH);

    testTokenization("(( a[a[b]] ))", EXPR_ARITH, WHITESPACE, ASSIGNMENT_WORD, LEFT_SQUARE, ASSIGNMENT_WORD, LEFT_SQUARE, WORD, RIGHT_SQUARE, RIGHT_SQUARE, WHITESPACE, _EXPR_ARITH);
    testTokenization("(( a[a[0]] ))", EXPR_ARITH, WHITESPACE, ASSIGNMENT_WORD, LEFT_SQUARE, ASSIGNMENT_WORD, LEFT_SQUARE, BashTokenTypes.NUMBER, RIGHT_SQUARE, RIGHT_SQUARE, WHITESPACE, _EXPR_ARITH);

    testTokenization("(( A[A[0]] ))", EXPR_ARITH, WHITESPACE, ASSIGNMENT_WORD, LEFT_SQUARE, ASSIGNMENT_WORD, LEFT_SQUARE, BashTokenTypes.NUMBER, RIGHT_SQUARE, RIGHT_SQUARE, WHITESPACE, _EXPR_ARITH);
  }

  @Test
  public void testIssue325() {
    testTokenization("cat << USE\nUSE", WORD, WHITESPACE, HEREDOC_MARKER_TAG, WHITESPACE, HEREDOC_MARKER_START, BashTokenTypes.LINEFEED, HEREDOC_MARKER_END);
    testTokenization("cat << THEUSAGE\nTHEUSAGE", WORD, WHITESPACE, HEREDOC_MARKER_TAG, WHITESPACE, HEREDOC_MARKER_START, BashTokenTypes.LINEFEED, HEREDOC_MARKER_END);
    //theUsage triggers a JFlex bug (apparently) if the marker regex includes [\s]
    testTokenization("cat << theUsage\ntheUsage", WORD, WHITESPACE, HEREDOC_MARKER_TAG, WHITESPACE, HEREDOC_MARKER_START, BashTokenTypes.LINEFEED, HEREDOC_MARKER_END);
    testTokenization("cat << _theUsage\n_theUsage", WORD, WHITESPACE, HEREDOC_MARKER_TAG, WHITESPACE, HEREDOC_MARKER_START, BashTokenTypes.LINEFEED, HEREDOC_MARKER_END);
  }

  @Test
  public void testIssue327() {
    testTokenization("<< EOF\n\\$(a)\nEOF", HEREDOC_MARKER_TAG, WHITESPACE, HEREDOC_MARKER_START, BashTokenTypes.LINEFEED, HEREDOC_CONTENT, HEREDOC_MARKER_END);
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
            "$1", LEFT_CURLY, BashTokenTypes.LINEFEED, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, BashTokenTypes.LINEFEED,
        HEREDOC_MARKER_END, BashTokenTypes.LINEFEED,
        DOLLAR, LEFT_CURLY, RIGHT_CURLY, HEREDOC_CONTENT,
        HEREDOC_MARKER_END, BashTokenTypes.LINEFEED,
        RIGHT_CURLY, BashTokenTypes.LINEFEED,
        VARIABLE
    );

    //incomplete ${ in a heredoc
    testTokenization("{\n" +
            "<<EOF <<EOF2\n" +
            "EOF\n" +
            "${\n" +
            "EOF2\n" +
            "}\n" +
            "$1", LEFT_CURLY, BashTokenTypes.LINEFEED, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, BashTokenTypes.LINEFEED,
        HEREDOC_MARKER_END, BashTokenTypes.LINEFEED,
        DOLLAR, LEFT_CURLY, BashTokenTypes.LINEFEED,
        HEREDOC_MARKER_END, BashTokenTypes.LINEFEED,
        RIGHT_CURLY, BashTokenTypes.LINEFEED,
        VARIABLE);

    //incomplete $( in a heredoc
    testTokenization("{\n" +
            "<<EOF <<EOF2\n" +
            "EOF\n" +
            "$(\n" +
            "EOF2\n" +
            ")\n" +
            "$1", LEFT_CURLY, BashTokenTypes.LINEFEED, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, BashTokenTypes.LINEFEED,
        HEREDOC_MARKER_END, BashTokenTypes.LINEFEED,
        DOLLAR, LEFT_PAREN, BashTokenTypes.LINEFEED,
        HEREDOC_MARKER_END, BashTokenTypes.LINEFEED,
        RIGHT_PAREN, BashTokenTypes.LINEFEED,
        VARIABLE
    );

    //incomplete $(( in a heredoc
    testTokenization("{\n" +
            "<<EOF <<EOF2\n" +
            "EOF\n" +
            "$((\n" +
            "EOF2\n" +
            ")\n" +
            "$1", LEFT_CURLY, BashTokenTypes.LINEFEED, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, BashTokenTypes.LINEFEED,
        HEREDOC_MARKER_END, BashTokenTypes.LINEFEED,
        DOLLAR, EXPR_ARITH, BashTokenTypes.LINEFEED,
        HEREDOC_MARKER_END, BashTokenTypes.LINEFEED,
        RIGHT_PAREN, BashTokenTypes.LINEFEED,
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
    testTokenization("trap", TRAP);
    testTokenization("trap -l", TRAP, WHITESPACE, WORD);
    testTokenization("trap -lp", TRAP, WHITESPACE, WORD);
    testTokenization("trap FUNCTION", TRAP, WHITESPACE, WORD);
    testTokenization("trap FUNCTION SIGINT", TRAP, WHITESPACE, WORD, WHITESPACE, WORD);
    testTokenization("trap -p SIGINT", TRAP, WHITESPACE, WORD, WHITESPACE, WORD);
  }

  @Test
  public void testEvalLexing() {
    testTokenization("$a=$a", VARIABLE, EQ, VARIABLE);
  }

  @Test
  public void testIssue376() {
    testTokenization("$(echo 2>&1)", DOLLAR, LEFT_PAREN, WORD, WHITESPACE, INT, GREATER_THAN, FILEDESCRIPTOR, RIGHT_PAREN);
    testTokenization("[[ $(echo 2>&1) ]]", LEFT_DOUBLE_BRACKET, DOLLAR, LEFT_PAREN, WORD, WHITESPACE, INT, GREATER_THAN, FILEDESCRIPTOR, RIGHT_PAREN, RIGHT_DOUBLE_BRACKET);
  }

  @Test
  public void testIssue367() {
    //invalid command semantic, but lexing needs to work
    testTokenization("[ (echo a) ]", EXPR_CONDITIONAL_LEFT, LEFT_PAREN, WORD, WHITESPACE, WORD, RIGHT_PAREN, EXPR_CONDITIONAL_RIGHT);

    testTokenization("[[ $(< $1) ]]", LEFT_DOUBLE_BRACKET, DOLLAR, LEFT_PAREN, LESS_THAN, WHITESPACE, VARIABLE, RIGHT_PAREN, RIGHT_DOUBLE_BRACKET);

    testTokenization("[[ $((1+1)) ]]", LEFT_DOUBLE_BRACKET, DOLLAR, EXPR_ARITH, BashTokenTypes.NUMBER, ARITH_PLUS, BashTokenTypes.NUMBER, _EXPR_ARITH, RIGHT_DOUBLE_BRACKET);
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
    testTokenization("a <<< [$((1))]", WORD, WHITESPACE, REDIRECT_HERE_STRING, WHITESPACE, WORD, DOLLAR, EXPR_ARITH, BashTokenTypes.NUMBER, _EXPR_ARITH, WORD);

    //comment after here string
    testTokenization("read <<< x\n#comment", WORD, WHITESPACE, REDIRECT_HERE_STRING, WHITESPACE, WORD, BashTokenTypes.LINEFEED, COMMENT);
    testTokenization("read <<< \"x\"\n#comment", WORD, WHITESPACE, REDIRECT_HERE_STRING, WHITESPACE, STRING_BEGIN, STRING_CONTENT, STRING_END, BashTokenTypes.LINEFEED, COMMENT);
    testTokenization("read <<< \"x\"\nif", WORD, WHITESPACE, REDIRECT_HERE_STRING, WHITESPACE, STRING_BEGIN, STRING_CONTENT, STRING_END, BashTokenTypes.LINEFEED, IF);
    testTokenization("read <<< \"x\"\na", WORD, WHITESPACE, REDIRECT_HERE_STRING, WHITESPACE, STRING_BEGIN, STRING_CONTENT, STRING_END, BashTokenTypes.LINEFEED, WORD);
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

    testTokenization("разработка программного обеспечения 2>&1", WORD, WHITESPACE, WORD, WHITESPACE, WORD, WHITESPACE, INT, GREATER_THAN, FILEDESCRIPTOR);

    testTokenization("α=1", ASSIGNMENT_WORD, EQ, INT);
    testTokenization("export α=1", WORD, WHITESPACE, ASSIGNMENT_WORD, EQ, INT);
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
        BashTokenTypes.CASE, WHITESPACE, WORD, WHITESPACE, WORD, BashTokenTypes.LINEFEED,
        WORD, PIPE, LINE_CONTINUATION, WORD, RIGHT_PAREN, BashTokenTypes.LINEFEED,
        WORD, BashTokenTypes.LINEFEED,
        CASE_END, BashTokenTypes.LINEFEED,
        ESAC);

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
        BashTokenTypes.CASE, WHITESPACE, WORD, WHITESPACE, WORD, BashTokenTypes.LINEFEED,
        LINE_CONTINUATION, WORD, PIPE, LINE_CONTINUATION, WHITESPACE, WORD, RIGHT_PAREN, BashTokenTypes.LINEFEED,
        WORD, BashTokenTypes.LINEFEED,
        CASE_END, BashTokenTypes.LINEFEED,
        ESAC);
  }

  @Test
  public void testIssue358() {
    //the problem with #398 was, that the lexer had a bad rule to leave unmatched characters and not return BAD_CHARACTER for all states at the end
    testTokenization("b & << EOF\n" +
        "d\n" +
        "EOF", WORD, WHITESPACE, AMP, WHITESPACE, HEREDOC_MARKER_TAG, WHITESPACE, HEREDOC_MARKER_START, BashTokenTypes.LINEFEED, HEREDOC_CONTENT, HEREDOC_MARKER_END);
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
    testTokenization("$((x|=5))", DOLLAR, EXPR_ARITH, WORD, ARITH_ASS_BIT_OR, BashTokenTypes.NUMBER, _EXPR_ARITH);
    testTokenization("$((x&=5))", DOLLAR, EXPR_ARITH, WORD, ARITH_ASS_BIT_AND, BashTokenTypes.NUMBER, _EXPR_ARITH);
    testTokenization("$((x^=5))", DOLLAR, EXPR_ARITH, WORD, ARITH_ASS_BIT_XOR, BashTokenTypes.NUMBER, _EXPR_ARITH);
  }

  @Test
  public void testIssue419() {
    testTokenization("$(x || x)", DOLLAR, LEFT_PAREN, WORD, WHITESPACE, OR_OR, WHITESPACE, WORD, RIGHT_PAREN);

    testTokenization("issues=($(x || x))", ASSIGNMENT_WORD, EQ, LEFT_PAREN, DOLLAR, LEFT_PAREN, WORD, WHITESPACE, OR_OR, WHITESPACE, WORD, RIGHT_PAREN, RIGHT_PAREN);
    testTokenization("issues=($(x && x))", ASSIGNMENT_WORD, EQ, LEFT_PAREN, DOLLAR, LEFT_PAREN, WORD, WHITESPACE, AND_AND, WHITESPACE, WORD, RIGHT_PAREN, RIGHT_PAREN);

    testTokenization("issues=($((1+1)))", ASSIGNMENT_WORD, EQ, LEFT_PAREN, DOLLAR, EXPR_ARITH, BashTokenTypes.NUMBER, ARITH_PLUS, BashTokenTypes.NUMBER, _EXPR_ARITH, RIGHT_PAREN);
  }

  @Test
  public void testIssue412() {
    testTokenization("[[ (a =~ \"b\") ]]", LEFT_DOUBLE_BRACKET, LEFT_PAREN, WORD, WHITESPACE, COND_OP_REGEX, WHITESPACE, STRING_BEGIN, STRING_CONTENT, STRING_END, RIGHT_PAREN, RIGHT_DOUBLE_BRACKET);
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
    testTokenization("a=\"a\\b_\"\n$a", ASSIGNMENT_WORD, EQ, STRING_BEGIN, STRING_CONTENT, STRING_END, BashTokenTypes.LINEFEED, VARIABLE);

        /*
        a="a"\
        "b_"
        $a
        */
    testTokenization("a=\"a\"\\\n\"b_\"\n$a", ASSIGNMENT_WORD, EQ, STRING_BEGIN, STRING_CONTENT, STRING_END, LINE_CONTINUATION, STRING_BEGIN, STRING_CONTENT, STRING_END, BashTokenTypes.LINEFEED, VARIABLE);
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
    testTokenization("`cat <<EOF\nX\nEOF`", BACKQUOTE, WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, BashTokenTypes.LINEFEED, HEREDOC_CONTENT, HEREDOC_MARKER_END, BACKQUOTE);

    // $(cat <<EOF
    // X
    // EOF
    // )
    testTokenization("$(cat <<EOF\nX\nEOF\n)", DOLLAR, LEFT_PAREN, WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, BashTokenTypes.LINEFEED, HEREDOC_CONTENT, HEREDOC_MARKER_END, BashTokenTypes.LINEFEED, RIGHT_PAREN);
  }

  @Test
  public void testIssue474() {
    //less-than should be replaced with a better token in the lexer
    testTokenization("cat <<EOF;\nX\nEOF", WORD, WHITESPACE, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START, SEMI, BashTokenTypes.LINEFEED, HEREDOC_CONTENT, HEREDOC_MARKER_END);
  }

  @Test
  public void testIssue505() {
    testTokenization("x<<<${2}", WORD, REDIRECT_HERE_STRING, DOLLAR, LEFT_CURLY, WORD, RIGHT_CURLY);
    testTokenization("x<<<${2}>/dev/null", WORD, REDIRECT_HERE_STRING, DOLLAR, LEFT_CURLY, WORD, RIGHT_CURLY, GREATER_THAN, WORD);

    testTokenization("foo() {\nif ! grep $1 <<< ${2} > /dev/null; then echo Boom; fi\n}",
        WORD, LEFT_PAREN, RIGHT_PAREN, WHITESPACE, LEFT_CURLY,
        BashTokenTypes.LINEFEED, IF, WHITESPACE, BANG, WHITESPACE, WORD, WHITESPACE, VARIABLE, WHITESPACE,
        REDIRECT_HERE_STRING, WHITESPACE, DOLLAR, LEFT_CURLY, WORD, RIGHT_CURLY, WHITESPACE,
        GREATER_THAN, WHITESPACE, WORD, SEMI, WHITESPACE,
        THEN, WHITESPACE, WORD, WHITESPACE, WORD, SEMI, WHITESPACE, FI, BashTokenTypes.LINEFEED, RIGHT_CURLY);
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
