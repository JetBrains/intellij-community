package com.jetbrains.python;

import com.intellij.lexer.Lexer;
import com.jetbrains.python.lexer.PythonIndentingLexer;
import junit.framework.TestCase;

/**
 * @author yole
 */
public class PythonLexerTest extends TestCase {
  public void testSimpleExpression() throws Exception {
    doTest("a=1", "Py:IDENTIFIER", "Py:EQ", "Py:INTEGER_LITERAL");
  }

  public void testMergeSpaces() throws Exception {
    doTest("a  =    1", "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:INTEGER_LITERAL");
  }

  public void testLineBreakInBraces() throws Exception {
    doTest("[a,\n b]", "Py:LBRACKET", "Py:IDENTIFIER", "Py:COMMA", "Py:LINE_BREAK", "Py:IDENTIFIER", "Py:RBRACKET");
  }

  public void testLineBreakInBraces2() throws Exception {
    doTest("x=[a,\n b]", "Py:IDENTIFIER", "Py:EQ", "Py:LBRACKET", "Py:IDENTIFIER", "Py:COMMA", "Py:LINE_BREAK",
           "Py:IDENTIFIER", "Py:RBRACKET");
  }

  public void testLineBreakInBracesAfterComment() throws Exception {
    doTest("x=[a, #c\n b]",
           "Py:IDENTIFIER", "Py:EQ", "Py:LBRACKET", "Py:IDENTIFIER", "Py:COMMA", "Py:SPACE", "Py:END_OF_LINE_COMMENT",
           "Py:LINE_BREAK", "Py:IDENTIFIER", "Py:RBRACKET");
  }

  public void testBraceAfterIndent() throws Exception {
    doTest("x=\n [a,\n  b]",
           "Py:IDENTIFIER", "Py:EQ", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:INDENT", "Py:LBRACKET", "Py:IDENTIFIER", "Py:COMMA", "Py:LINE_BREAK", "Py:IDENTIFIER", "Py:RBRACKET");
  }

  public void testBackslash() throws Exception {
    doTest("a=\\\nb",
           "Py:IDENTIFIER", "Py:EQ", "Py:SPACE", "Py:LINE_BREAK", "Py:IDENTIFIER");
  }

  public void testBackslashAfterSpace() throws Exception {
    doTest("a = \\\n  b",
           "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:LINE_BREAK", "Py:IDENTIFIER");
  }

  public void testMultipleBackslashes() throws Exception {
    doTest("[\\\n\\\n]",
           "Py:LBRACKET", "Py:SPACE", "Py:LINE_BREAK", "Py:SPACE", "Py:LINE_BREAK", "Py:RBRACKET");
  }

  public void testIndent() throws Exception {
    doTest("if a:\n b",
           "Py:IF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:COLON", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:INDENT", "Py:IDENTIFIER");
  }

  public void testMultiLineIndent() throws Exception {
    doTest("if a:\n b\n c",
           "Py:IF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:COLON", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:INDENT", "Py:IDENTIFIER", "Py:STATEMENT_BREAK", "Py:LINE_BREAK", "Py:IDENTIFIER");
  }

  public void testDedent() throws Exception {
    doTest("if a:\n b\nc",
           "Py:IF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:COLON", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:INDENT", "Py:IDENTIFIER", "Py:STATEMENT_BREAK", "Py:DEDENT", "Py:LINE_BREAK",
           "Py:IDENTIFIER");
  }

  public void testMultiDedent() throws Exception {
    doTest("if a:\n b\n  c\nd",
           "Py:IF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:COLON", "Py:STATEMENT_BREAK", "Py:LINE_BREAK", "Py:INDENT",
           "Py:IDENTIFIER", "Py:STATEMENT_BREAK", "Py:LINE_BREAK", "Py:INDENT", "Py:IDENTIFIER", "Py:STATEMENT_BREAK", "Py:DEDENT",
           "Py:DEDENT", "Py:LINE_BREAK", "Py:IDENTIFIER");
  }

  public void testEmptyLine() throws Exception {
    doTest("if a:\n b\n  \n c",
           "Py:IF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:COLON", "Py:STATEMENT_BREAK", "Py:LINE_BREAK", "Py:INDENT",
           "Py:IDENTIFIER", "Py:STATEMENT_BREAK", "Py:LINE_BREAK", "Py:IDENTIFIER");
  }

  public void testEndOfLineSpace() throws Exception {
    doTest("if a:\n b\n  c   \n  \n  d",
           "Py:IF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:COLON", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:INDENT", "Py:IDENTIFIER", "Py:STATEMENT_BREAK", "Py:LINE_BREAK", "Py:INDENT", "Py:IDENTIFIER", "Py:STATEMENT_BREAK",
           "Py:LINE_BREAK", "Py:IDENTIFIER");
  }

  public void testComment() throws Exception {
    doTest("if a:\n b\n #comment\nc",
           "Py:IF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:COLON", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:INDENT", "Py:IDENTIFIER", "Py:STATEMENT_BREAK", "Py:LINE_BREAK", "Py:END_OF_LINE_COMMENT", "Py:DEDENT", "Py:LINE_BREAK",
           "Py:IDENTIFIER");
  }

  public void testIndentedComment() throws Exception {
    doTest("#a\n #b\n#c",
           "Py:END_OF_LINE_COMMENT", "Py:LINE_BREAK", "Py:END_OF_LINE_COMMENT", "Py:LINE_BREAK", "Py:END_OF_LINE_COMMENT");
  }

  public void testIndentedCommentAndCode() throws Exception {
    doTest("if a:\n #b\n c",
           "Py:IF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:COLON", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:END_OF_LINE_COMMENT", "Py:LINE_BREAK", "Py:INDENT", "Py:IDENTIFIER");
  }

  public void testWithNotKeyword() throws Exception {
    doTest("with=as", "Py:IDENTIFIER", "Py:EQ", "Py:IDENTIFIER");
  }

  public void testBytesLiteral() throws Exception {
    doTest("b'ABC'", "Py:STRING_LITERAL");
  }

  public void testOctalLiteral() throws Exception {
    doTest("0o123", "Py:INTEGER_LITERAL");
  }

  public void testBinaryLiteral() throws Exception {
    doTest("0b0101", "Py:INTEGER_LITERAL");
  }

  public void _testWithKeyword() throws Exception {
    // processing of 'from __future__ import' is now done on parser level, so a pure lexer test won't handle
    // this correctly
    doTest("from __future__ import with_statement\nwith x as y", new String[] { "Py:FROM_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:SPACE", "Py:IMPORT_KEYWORD", "Py:SPACE", "Py:IDENTIFIER",
           "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:WITH_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:SPACE", "Py:AS_KEYWORD", "Py:SPACE", "Py:IDENTIFIER" });
  }

  private static void doTest(String text, String... expectedTokens) {
    Lexer lexer = new PythonIndentingLexer();
    lexer.start(text);
    int idx = 0;
    int tokenPos = 0;
    while (lexer.getTokenType() != null) {
      if (idx > expectedTokens.length) fail("Too many tokens");
      assertEquals("Token offset mismatch at position " + idx, tokenPos, lexer.getTokenStart());
      String tokenName = lexer.getTokenType().toString();
      assertEquals("Token mismatch at position " + idx, expectedTokens[idx], tokenName);
      idx++;
      tokenPos = lexer.getTokenEnd();
      lexer.advance();
    }

    if (idx < expectedTokens.length) fail("Not enough tokens");
  }
}
