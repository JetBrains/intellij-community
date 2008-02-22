/*
 *  Copyright 2005 Pythonid Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS"; BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.jetbrains.python;

import com.intellij.lexer.Lexer;
import junit.framework.TestCase;
import com.jetbrains.python.PythonIndentingLexer;

/**
 * @author yole
 */
public class PythonLexerTest extends TestCase {
  public void testSimpleExpression() throws Exception {
    doTest("a=1", new String[]{"Py:IDENTIFIER", "Py:EQ", "Py:INTEGER_LITERAL"});
  }

  public void testMergeSpaces() throws Exception {
    doTest("a  =    1", new String[]{"Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:INTEGER_LITERAL"});
  }

  public void testLineBreakInBraces() throws Exception {
    doTest("[a,\n b]", new String[]{"Py:LBRACKET", "Py:IDENTIFIER", "Py:COMMA", "Py:LINE_BREAK", "Py:IDENTIFIER", "Py:RBRACKET"});
  }

  public void testLineBreakInBraces2() throws Exception {
    doTest("x=[a,\n b]", new String[]{"Py:IDENTIFIER", "Py:EQ", "Py:LBRACKET", "Py:IDENTIFIER", "Py:COMMA", "Py:LINE_BREAK",
      "Py:IDENTIFIER", "Py:RBRACKET"});
  }

  public void testLineBreakInBracesAfterComment() throws Exception {
    doTest("x=[a, #c\n b]", new String[]{"Py:IDENTIFIER", "Py:EQ", "Py:LBRACKET", "Py:IDENTIFIER", "Py:COMMA", "Py:SPACE",
      "Py:END_OF_LINE_COMMENT", "Py:LINE_BREAK", "Py:IDENTIFIER", "Py:RBRACKET"});
  }

  public void testBraceAfterIndent() throws Exception {
    doTest("x=\n [a,\n  b]", new String[]{"Py:IDENTIFIER", "Py:EQ", "Py:STATEMENT_BREAK", "Py:LINE_BREAK", "Py:INDENT", "Py:LBRACKET",
      "Py:IDENTIFIER", "Py:COMMA", "Py:LINE_BREAK", "Py:IDENTIFIER", "Py:RBRACKET"});
  }

  public void testBackslash() throws Exception {
    doTest("a=\\\nb", new String[]{"Py:IDENTIFIER", "Py:EQ", "Py:SPACE", "Py:LINE_BREAK", "Py:IDENTIFIER"});
  }

  public void testBackslashAfterSpace() throws Exception {
    doTest("a = \\\n  b", new String[]{"Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:LINE_BREAK", "Py:IDENTIFIER"});
  }

  public void testIndent() throws Exception {
    doTest("if a:\n b", new String[]{"Py:IF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:COLON", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
      "Py:INDENT", "Py:IDENTIFIER"});
  }

  public void testMultiLineIndent() throws Exception {
    doTest("if a:\n b\n c", new String[]{"Py:IF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:COLON", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
      "Py:INDENT", "Py:IDENTIFIER", "Py:STATEMENT_BREAK", "Py:LINE_BREAK", "Py:IDENTIFIER"});
  }

  public void testDedent() throws Exception {
    doTest("if a:\n b\nc", new String[]{"Py:IF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:COLON", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
      "Py:INDENT", "Py:IDENTIFIER", "Py:STATEMENT_BREAK", "Py:DEDENT", "Py:LINE_BREAK", "Py:IDENTIFIER"});
  }

  public void testMultiDedent() throws Exception {
    doTest("if a:\n b\n  c\nd", new String[]{"Py:IF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:COLON", "Py:STATEMENT_BREAK",
      "Py:LINE_BREAK", "Py:INDENT", "Py:IDENTIFIER", "Py:STATEMENT_BREAK", "Py:LINE_BREAK", "Py:INDENT", "Py:IDENTIFIER",
      "Py:STATEMENT_BREAK", "Py:DEDENT", "Py:DEDENT", "Py:LINE_BREAK", "Py:IDENTIFIER"});
  }

  public void testEmptyLine() throws Exception {
    doTest("if a:\n b\n  \n c", new String[]{"Py:IF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:COLON", "Py:STATEMENT_BREAK",
      "Py:LINE_BREAK", "Py:INDENT", "Py:IDENTIFIER", "Py:STATEMENT_BREAK", "Py:LINE_BREAK", "Py:IDENTIFIER"});
  }

  public void testEndOfLineSpace() throws Exception {
    doTest("if a:\n b\n  c   \n  \n  d", new String[]{"Py:IF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:COLON", "Py:STATEMENT_BREAK",
      "Py:LINE_BREAK", "Py:INDENT", "Py:IDENTIFIER", "Py:STATEMENT_BREAK", "Py:LINE_BREAK", "Py:INDENT", "Py:IDENTIFIER",
      "Py:STATEMENT_BREAK", "Py:LINE_BREAK", "Py:IDENTIFIER"});
  }

  public void testComment() throws Exception {
    doTest("if a:\n b\n #comment\nc", new String[]{"Py:IF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:COLON", "Py:STATEMENT_BREAK",
      "Py:LINE_BREAK", "Py:INDENT", "Py:IDENTIFIER", "Py:STATEMENT_BREAK", "Py:LINE_BREAK", "Py:END_OF_LINE_COMMENT", "Py:DEDENT",
      "Py:LINE_BREAK", "Py:IDENTIFIER"});
  }

  public void testIndentedComment() throws Exception {
    doTest("#a\n #b\n#c",
           new String[]{"Py:END_OF_LINE_COMMENT", "Py:LINE_BREAK", "Py:END_OF_LINE_COMMENT", "Py:LINE_BREAK", "Py:END_OF_LINE_COMMENT"});
  }

  public void testIndentedCommentAndCode() throws Exception {
    doTest("if a:\n #b\n c", new String[]{"Py:IF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:COLON", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
      "Py:END_OF_LINE_COMMENT", "Py:LINE_BREAK", "Py:INDENT", "Py:IDENTIFIER"});
  }

  public void testWithNotKeyword() throws Exception {
    doTest("with=as", new String[] { "Py:IDENTIFIER", "Py:EQ", "Py:IDENTIFIER"});
  }

  public void testWithKeyword() throws Exception {
    doTest("from __future__ import with_statement\nwith x as y", new String[] { "Py:FROM_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:SPACE", "Py:IMPORT_KEYWORD", "Py:SPACE", "Py:IDENTIFIER",
           "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:WITH_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:SPACE", "Py:AS_KEYWORD", "Py:SPACE", "Py:IDENTIFIER" });
  }

  private void doTest(String text, String[] expectedTokens) {
    Lexer lexer = new PythonIndentingLexer();
    lexer.start(text.toCharArray());
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
