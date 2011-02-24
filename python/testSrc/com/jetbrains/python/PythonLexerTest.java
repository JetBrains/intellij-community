package com.jetbrains.python;

import com.jetbrains.python.fixtures.PyLexerTestCase;
import com.jetbrains.python.lexer.PythonIndentingLexer;

/**
 * @author yole
 */
public class PythonLexerTest extends PyLexerTestCase {
  public void testSimpleExpression() {
    doTest("a=1", "Py:IDENTIFIER", "Py:EQ", "Py:INTEGER_LITERAL");
  }

  public void testMergeSpaces() {
    doTest("a  =    1", "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:INTEGER_LITERAL");
  }

  public void testLineBreakInBraces() {
    doTest("[a,\n b]", "Py:LBRACKET", "Py:IDENTIFIER", "Py:COMMA", "Py:LINE_BREAK", "Py:IDENTIFIER", "Py:RBRACKET");
  }

  public void testLineBreakInBraces2() {
    doTest("x=[a,\n b]", "Py:IDENTIFIER", "Py:EQ", "Py:LBRACKET", "Py:IDENTIFIER", "Py:COMMA", "Py:LINE_BREAK",
           "Py:IDENTIFIER", "Py:RBRACKET");
  }

  public void testLineBreakInBracesAfterComment() {
    doTest("x=[a, #c\n b]",
           "Py:IDENTIFIER", "Py:EQ", "Py:LBRACKET", "Py:IDENTIFIER", "Py:COMMA", "Py:SPACE", "Py:END_OF_LINE_COMMENT",
           "Py:LINE_BREAK", "Py:IDENTIFIER", "Py:RBRACKET");
  }

  public void testBraceAfterIndent() {
    doTest("x=\n [a,\n  b]",
           "Py:IDENTIFIER", "Py:EQ", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:INDENT", "Py:LBRACKET", "Py:IDENTIFIER", "Py:COMMA", "Py:LINE_BREAK", "Py:IDENTIFIER", "Py:RBRACKET");
  }

  public void testBackslash() {
    doTest("a=\\\nb",
           "Py:IDENTIFIER", "Py:EQ", "Py:SPACE", "Py:LINE_BREAK", "Py:IDENTIFIER");
  }

  public void testBackslashAfterSpace() {
    doTest("a = \\\n  b",
           "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:LINE_BREAK", "Py:IDENTIFIER");
  }

  public void testMultipleBackslashes() {
    doTest("[\\\n\\\n]",
           "Py:LBRACKET", "Py:SPACE", "Py:LINE_BREAK", "Py:SPACE", "Py:LINE_BREAK", "Py:RBRACKET");
  }

  public void testIndent() {
    doTest("if a:\n b",
           "Py:IF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:COLON", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:INDENT", "Py:IDENTIFIER");
  }

  public void testMultiLineIndent() {
    doTest("if a:\n b\n c",
           "Py:IF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:COLON", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:INDENT", "Py:IDENTIFIER", "Py:STATEMENT_BREAK", "Py:LINE_BREAK", "Py:IDENTIFIER");
  }

  public void testDedent() {
    doTest("if a:\n b\nc",
           "Py:IF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:COLON", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:INDENT", "Py:IDENTIFIER", "Py:STATEMENT_BREAK", "Py:DEDENT", "Py:LINE_BREAK",
           "Py:IDENTIFIER");
  }

  public void testMultiDedent() {
    doTest("if a:\n b\n  c\nd",
           "Py:IF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:COLON", "Py:STATEMENT_BREAK", "Py:LINE_BREAK", "Py:INDENT",
           "Py:IDENTIFIER", "Py:STATEMENT_BREAK", "Py:LINE_BREAK", "Py:INDENT", "Py:IDENTIFIER", "Py:STATEMENT_BREAK", "Py:DEDENT",
           "Py:DEDENT", "Py:LINE_BREAK", "Py:IDENTIFIER");
  }

  public void testEmptyLine() {
    doTest("if a:\n b\n  \n c",
           "Py:IF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:COLON", "Py:STATEMENT_BREAK", "Py:LINE_BREAK", "Py:INDENT",
           "Py:IDENTIFIER", "Py:STATEMENT_BREAK", "Py:LINE_BREAK", "Py:IDENTIFIER");
  }

  public void testEndOfLineSpace() {
    doTest("if a:\n b\n  c   \n  \n  d",
           "Py:IF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:COLON", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:INDENT", "Py:IDENTIFIER", "Py:STATEMENT_BREAK", "Py:LINE_BREAK", "Py:INDENT", "Py:IDENTIFIER", "Py:STATEMENT_BREAK",
           "Py:LINE_BREAK", "Py:IDENTIFIER");
  }

  public void testComment() {
    doTest("if a:\n b\n #comment\nc",
           "Py:IF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:COLON", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:INDENT", "Py:IDENTIFIER", "Py:STATEMENT_BREAK", "Py:LINE_BREAK", "Py:END_OF_LINE_COMMENT", "Py:DEDENT", "Py:LINE_BREAK",
           "Py:IDENTIFIER");
  }

  public void testIndentedComment() {
    doTest("#a\n #b\n#c",
           "Py:END_OF_LINE_COMMENT", "Py:LINE_BREAK", "Py:END_OF_LINE_COMMENT", "Py:LINE_BREAK", "Py:END_OF_LINE_COMMENT");
  }

  public void testIndentedCommentAndCode() {
    doTest("if a:\n #b\n c",
           "Py:IF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:COLON", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:INDENT", "Py:END_OF_LINE_COMMENT", "Py:LINE_BREAK", "Py:IDENTIFIER");
  }

  public void testWithNotKeyword() {
    doTest("with=as", "Py:IDENTIFIER", "Py:EQ", "Py:IDENTIFIER");
  }

  public void testBytesLiteral() {
    doTest("b'ABC'", "Py:STRING_LITERAL");
  }

  public void testOctalLiteral() {
    doTest("0o123", "Py:INTEGER_LITERAL");
  }

  public void testBinaryLiteral() {
    doTest("0b0101", "Py:INTEGER_LITERAL");
  }

  private static final String THREE_QUOTES = "\"\"\"";

  public void testLongString() {
    doTest(THREE_QUOTES + THREE_QUOTES + "\nb=" + THREE_QUOTES + THREE_QUOTES,
           "Py:STRING_LITERAL", "Py:STATEMENT_BREAK", "Py:LINE_BREAK", "Py:IDENTIFIER", "Py:EQ", "Py:STRING_LITERAL");
  }

  public void testLongStringEscaped() {
    doTest("''' \\'''foo\\''' ''';", "Py:STRING_LITERAL", "Py:SEMICOLON");
  }

  public void testLongStringWithLineBreak() {
    doTest(THREE_QUOTES + "\\\na\n\n" + THREE_QUOTES + ";", "Py:STRING_LITERAL", "Py:SEMICOLON");
  }


  public void _testWithKeyword() throws Exception {
    // processing of 'from __future__ import' is now done on parser level, so a pure lexer test won't handle
    // this correctly
    doTest("from __future__ import with_statement\nwith x as y", new String[] { "Py:FROM_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:SPACE", "Py:IMPORT_KEYWORD", "Py:SPACE", "Py:IDENTIFIER",
           "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:WITH_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:SPACE", "Py:AS_KEYWORD", "Py:SPACE", "Py:IDENTIFIER" });
  }

  public void testBackslashBeforeEmptyLine() {
    doTest("x=\"a\" + \\\n     \"b\" \\\n\nprint x", "Py:IDENTIFIER", "Py:EQ", "Py:STRING_LITERAL", "Py:SPACE", "Py:PLUS", "Py:SPACE", "Py:LINE_BREAK",
           "Py:STRING_LITERAL", "Py:SPACE", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:IDENTIFIER" , "Py:SPACE", "Py:IDENTIFIER");
  }

  public void testIncompleteTripleQuotedString() {  // PY-1768
    doTest("'''abc\nd", "Py:STRING_LITERAL");
  }

  public void testEscapedClosingTripleApos() {  // PY-1777
    doTest("''' foo '\\''' bar '''", "Py:STRING_LITERAL");
  }

  public void testEscapedClosingTripleQuote() {  // PY-1777
    doTest(THREE_QUOTES + " foo \"\\" + THREE_QUOTES + " bar " + THREE_QUOTES, "Py:STRING_LITERAL");
  }

  public void testOddNumberOfQuotes() {  // PY-2802
    doTest("'''foo''''", "Py:STRING_LITERAL", "Py:STRING_LITERAL");
  }

  public void testDedentBeforeComment() {  // PY-2209 & friends
    doTest("class UserProfile:\n" +
           "    pass\n" +
           "\n" +
           "#noinspection PyUnusedLocal\n" +
           "def foo(sender):\n" +
           "    pass",
           "Py:CLASS_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:COLON", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:INDENT", "Py:PASS_KEYWORD", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:DEDENT", "Py:END_OF_LINE_COMMENT", "Py:LINE_BREAK",
           "Py:DEF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:LPAR", "Py:IDENTIFIER", "Py:RPAR", "Py:COLON", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:INDENT", "Py:PASS_KEYWORD");
  }

  public void testDedentAfterComment() { // PY-2137
    doTest("def foo():\n" +
           "    pass\n" +
           "    #comment\n",
           "Py:DEF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:LPAR", "Py:RPAR", "Py:COLON", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:INDENT", "Py:PASS_KEYWORD", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:END_OF_LINE_COMMENT", "Py:DEDENT", "Py:LINE_BREAK");
  }

  private static void doTest(String text, String... expectedTokens) {
    doLexerTest(text, new PythonIndentingLexer(), expectedTokens);
  }
}
