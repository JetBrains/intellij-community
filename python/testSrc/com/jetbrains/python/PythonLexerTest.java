// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.jetbrains.python.fixtures.PyLexerTestCase;
import com.jetbrains.python.lexer.PythonIndentingLexer;


public class PythonLexerTest extends PyLexerTestCase {
  public void testSimpleExpression() {
    doTest("a=1", "Py:IDENTIFIER", "Py:EQ", "Py:INTEGER_LITERAL", "Py:STATEMENT_BREAK");
  }

  public void testMergeSpaces() {
    doTest("a  =    1", "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:INTEGER_LITERAL", "Py:STATEMENT_BREAK");
  }

  public void testLineBreakInBraces() {
    doTest("[a,\n b]", "Py:LBRACKET", "Py:IDENTIFIER", "Py:COMMA", "Py:LINE_BREAK", "Py:IDENTIFIER", "Py:RBRACKET", "Py:STATEMENT_BREAK");
  }

  public void testLineBreakInBraces2() {
    doTest("x=[a,\n b]", "Py:IDENTIFIER", "Py:EQ", "Py:LBRACKET", "Py:IDENTIFIER", "Py:COMMA", "Py:LINE_BREAK",
           "Py:IDENTIFIER", "Py:RBRACKET", "Py:STATEMENT_BREAK");
  }

  public void testLineBreakInBracesAfterComment() {
    doTest("x=[a, #c\n b]",
           "Py:IDENTIFIER", "Py:EQ", "Py:LBRACKET", "Py:IDENTIFIER", "Py:COMMA", "Py:SPACE", "Py:END_OF_LINE_COMMENT",
           "Py:LINE_BREAK", "Py:IDENTIFIER", "Py:RBRACKET", "Py:STATEMENT_BREAK");
  }

  public void testBraceAfterIndent() {
    doTest("x=\n [a,\n  b]",
           "Py:IDENTIFIER", "Py:EQ", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:INDENT", "Py:LBRACKET", "Py:IDENTIFIER", "Py:COMMA", "Py:LINE_BREAK", "Py:IDENTIFIER", "Py:RBRACKET", "Py:STATEMENT_BREAK");
  }

  public void testBackslash() {
    doTest("a=\\\nb",
           "Py:IDENTIFIER", "Py:EQ", "Py:SPACE", "Py:LINE_BREAK", "Py:IDENTIFIER", "Py:STATEMENT_BREAK");
  }

  public void testBackslashAfterSpace() {
    doTest("a = \\\n  b",
           "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:LINE_BREAK", "Py:IDENTIFIER", "Py:STATEMENT_BREAK");
  }

  public void testMultipleBackslashes() {
    doTest("[\\\n\\\n]",
           "Py:LBRACKET", "Py:SPACE", "Py:LINE_BREAK", "Py:SPACE", "Py:LINE_BREAK", "Py:RBRACKET", "Py:STATEMENT_BREAK");
  }

  public void testIndent() {
    doTest("if a:\n b",
           "Py:IF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:COLON", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:INDENT", "Py:IDENTIFIER", "Py:STATEMENT_BREAK");
  }

  public void testMultiLineIndent() {
    doTest("if a:\n b\n c",
           "Py:IF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:COLON", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:INDENT", "Py:IDENTIFIER", "Py:STATEMENT_BREAK", "Py:LINE_BREAK", "Py:IDENTIFIER", "Py:STATEMENT_BREAK");
  }

  public void testDedent() {
    doTest("if a:\n b\nc",
           "Py:IF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:COLON", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:INDENT", "Py:IDENTIFIER", "Py:STATEMENT_BREAK", "Py:DEDENT", "Py:LINE_BREAK",
           "Py:IDENTIFIER", "Py:STATEMENT_BREAK");
  }

  public void testMultiDedent() {
    doTest("if a:\n b\n  c\nd",
           "Py:IF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:COLON", "Py:STATEMENT_BREAK", "Py:LINE_BREAK", "Py:INDENT",
           "Py:IDENTIFIER", "Py:STATEMENT_BREAK", "Py:LINE_BREAK", "Py:INDENT", "Py:IDENTIFIER", "Py:STATEMENT_BREAK", "Py:DEDENT",
           "Py:DEDENT", "Py:LINE_BREAK", "Py:IDENTIFIER", "Py:STATEMENT_BREAK");
  }

  public void testEmptyLine() {
    doTest("if a:\n b\n  \n c",
           "Py:IF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:COLON", "Py:STATEMENT_BREAK", "Py:LINE_BREAK", "Py:INDENT",
           "Py:IDENTIFIER", "Py:STATEMENT_BREAK", "Py:LINE_BREAK", "Py:IDENTIFIER", "Py:STATEMENT_BREAK");
  }

  public void testEndOfLineSpace() {
    doTest("if a:\n b\n  c   \n  \n  d",
           "Py:IF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:COLON", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:INDENT", "Py:IDENTIFIER", "Py:STATEMENT_BREAK", "Py:LINE_BREAK", "Py:INDENT", "Py:IDENTIFIER", "Py:STATEMENT_BREAK",
           "Py:LINE_BREAK", "Py:IDENTIFIER", "Py:STATEMENT_BREAK");
  }

  public void testComment() {
    doTest("if a:\n b\n #comment\nc",
           "Py:IF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:COLON", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:INDENT", "Py:IDENTIFIER", "Py:STATEMENT_BREAK", "Py:LINE_BREAK", "Py:END_OF_LINE_COMMENT", "Py:DEDENT", "Py:LINE_BREAK",
           "Py:IDENTIFIER", "Py:STATEMENT_BREAK");
  }

  public void testIndentedComment() {
    doTest("#a\n #b\n#c",
           "Py:END_OF_LINE_COMMENT", "Py:LINE_BREAK", "Py:END_OF_LINE_COMMENT", "Py:LINE_BREAK", "Py:END_OF_LINE_COMMENT", "Py:STATEMENT_BREAK");
  }

  public void testIndentedCommentAndCode() {
    doTest("if a:\n #b\n c",
           "Py:IF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:COLON", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:INDENT", "Py:END_OF_LINE_COMMENT", "Py:LINE_BREAK", "Py:IDENTIFIER", "Py:STATEMENT_BREAK");
  }

  public void testWithNotKeyword() {
    doTest("with=as", "Py:IDENTIFIER", "Py:EQ", "Py:IDENTIFIER", "Py:STATEMENT_BREAK");
  }

  public void testBytesLiteral() {
    doTest("a=b'ABC'", "Py:IDENTIFIER", "Py:EQ", "Py:SINGLE_QUOTED_STRING", "Py:STATEMENT_BREAK");
  }

  public void testOctalLiteral() {
    doTest("0o123", "Py:INTEGER_LITERAL", "Py:STATEMENT_BREAK");
  }

  public void testBinaryLiteral() {
    doTest("0b0101", "Py:INTEGER_LITERAL", "Py:STATEMENT_BREAK");
  }

  private static final String THREE_QUOTES = "\"\"\"";

  public void testLongString() {
    doTest(THREE_QUOTES + THREE_QUOTES + "\nb=" + THREE_QUOTES + THREE_QUOTES,
           "Py:DOCSTRING", "Py:STATEMENT_BREAK", "Py:LINE_BREAK", "Py:IDENTIFIER", "Py:EQ", "Py:TRIPLE_QUOTED_STRING", "Py:STATEMENT_BREAK");
  }

  public void testLongStringEscaped() {
    doTest("''' \\'''foo\\''' ''';", "Py:DOCSTRING", "Py:SEMICOLON", "Py:STATEMENT_BREAK");
  }

  public void testLongStringWithLineBreak() {
    doTest(THREE_QUOTES + "\\\na\n\n" + THREE_QUOTES + ";", "Py:DOCSTRING", "Py:SEMICOLON", "Py:STATEMENT_BREAK");
  }


  public void _testWithKeyword() {
    // processing of 'from __future__ import' is now done on parser level, so a pure lexer test won't handle
    // this correctly
    doTest("from __future__ import with_statement\nwith x as y", "Py:FROM_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:SPACE",
           "Py:IMPORT_KEYWORD", "Py:SPACE", "Py:IDENTIFIER",
           "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:WITH_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:SPACE", "Py:AS_KEYWORD", "Py:SPACE", "Py:IDENTIFIER");
  }

  public void testBackslashBeforeEmptyLine() {
    doTest("x=\"a\" + \\\n     \"b\" \\\n\nprint x", "Py:IDENTIFIER", "Py:EQ", "Py:SINGLE_QUOTED_STRING", "Py:SPACE", "Py:PLUS", "Py:SPACE", "Py:LINE_BREAK",
           "Py:SINGLE_QUOTED_STRING", "Py:SPACE", "Py:LINE_BREAK", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:IDENTIFIER" , "Py:SPACE", "Py:IDENTIFIER", "Py:STATEMENT_BREAK");
  }

  public void testBackslashFuction() {
    doTest("""
             def test():
                 print \\

             def test2():
                 pass

             print 'hello'""", "Py:DEF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:LPAR", "Py:RPAR", "Py:COLON", "Py:STATEMENT_BREAK",
           "Py:LINE_BREAK", "Py:INDENT", "Py:IDENTIFIER", "Py:SPACE", "Py:LINE_BREAK", "Py:STATEMENT_BREAK", "Py:DEDENT", "Py:LINE_BREAK",
           "Py:DEF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:LPAR", "Py:RPAR", "Py:COLON", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:INDENT", "Py:PASS_KEYWORD", "Py:STATEMENT_BREAK", "Py:DEDENT", "Py:LINE_BREAK", "Py:IDENTIFIER", "Py:SPACE",
           "Py:SINGLE_QUOTED_STRING", "Py:STATEMENT_BREAK");
  }

  public void testIncompleteTripleQuotedString() {  // PY-1768
    doTest("tmp = '''abc\nd",  "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:TRIPLE_QUOTED_STRING", "Py:STATEMENT_BREAK");
  }

  public void testEscapedClosingTripleApos() {  // PY-1777
    doTest("a=''' foo '\\''' bar '''", "Py:IDENTIFIER", "Py:EQ", "Py:TRIPLE_QUOTED_STRING", "Py:STATEMENT_BREAK");
  }

  public void testEscapedClosingTripleQuote() {  // PY-1777
    doTest("a="+THREE_QUOTES + " foo \"\\" + THREE_QUOTES + " bar " + THREE_QUOTES, "Py:IDENTIFIER", "Py:EQ", "Py:TRIPLE_QUOTED_STRING", "Py:STATEMENT_BREAK");
  }

  public void testEOFDocstring() {  // PY-4169
    doTest(THREE_QUOTES + " foo \"\\" + THREE_QUOTES + " bar " + THREE_QUOTES,"Py:DOCSTRING", "Py:STATEMENT_BREAK");
  }

  public void testOddNumberOfQuotes() {  // PY-2802
    doTest("'''foo''''", "Py:TRIPLE_QUOTED_STRING", "Py:SINGLE_QUOTED_STRING", "Py:STATEMENT_BREAK");
  }

  public void testDedentBeforeComment() {  // PY-2209 & friends
    doTest("""
             class UserProfile:
                 pass

             #noinspection PyUnusedLocal
             def foo(sender):
                 pass""",
           "Py:CLASS_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:COLON", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:INDENT", "Py:PASS_KEYWORD", "Py:STATEMENT_BREAK",
           "Py:DEDENT", "Py:LINE_BREAK", "Py:END_OF_LINE_COMMENT", "Py:LINE_BREAK",
           "Py:DEF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:LPAR", "Py:IDENTIFIER", "Py:RPAR", "Py:COLON", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:INDENT", "Py:PASS_KEYWORD", "Py:STATEMENT_BREAK");
  }

  public void testDedentAfterComment() { // PY-2137
    doTest("""
             def foo():
                 pass
                 #comment
             """,
           "Py:DEF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:LPAR", "Py:RPAR", "Py:COLON", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:INDENT", "Py:PASS_KEYWORD", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:END_OF_LINE_COMMENT", "Py:DEDENT", "Py:LINE_BREAK", "Py:STATEMENT_BREAK");
  }
  
  public void testIndentAtStartOfFile() {  // PY-4941
    doTest("   a", "Py:SPACE", "Py:INDENT", "Py:IDENTIFIER", "Py:STATEMENT_BREAK");
  }

  // PY-3067
  public void testErrorOpenParInExpr() {
    doTest("""
             def f():
                 (

             def g():
                 pass
             """,
           "Py:DEF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:LPAR", "Py:RPAR", "Py:COLON", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:INDENT", "Py:LPAR", "Py:LINE_BREAK",
           "Py:STATEMENT_BREAK", "Py:DEDENT", "Py:LINE_BREAK", // Error recovery
           "Py:DEF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:LPAR", "Py:RPAR", "Py:COLON", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:INDENT", "Py:PASS_KEYWORD", "Py:STATEMENT_BREAK", "Py:DEDENT", "Py:LINE_BREAK",
           "Py:STATEMENT_BREAK");
  }

  // PY-3067
  public void testErrorOpenParInExprBeforeComment() {
    doTest("""
             def f():
                 (
             #comment
             def g():
                 pass
             """,
           "Py:DEF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:LPAR", "Py:RPAR", "Py:COLON", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:INDENT", "Py:LPAR", "Py:LINE_BREAK",
           "Py:END_OF_LINE_COMMENT", "Py:LINE_BREAK",
           "Py:STATEMENT_BREAK", "Py:DEDENT", "Py:LINE_BREAK", // Error recovery
           "Py:DEF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:LPAR", "Py:RPAR", "Py:COLON", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:INDENT", "Py:PASS_KEYWORD", "Py:STATEMENT_BREAK", "Py:DEDENT", "Py:LINE_BREAK",
           "Py:STATEMENT_BREAK");
  }


  // PY-7255
  public void testDocstringInDict() {
    doTest("""
             d = {
                 'foo':
                     'bar'
                     'baz'
             }""",
           "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:LBRACE", "Py:LINE_BREAK",
           "Py:SINGLE_QUOTED_STRING", "Py:COLON", "Py:LINE_BREAK", "Py:SINGLE_QUOTED_STRING", "Py:LINE_BREAK",
           "Py:SINGLE_QUOTED_STRING", "Py:LINE_BREAK", "Py:RBRACE", "Py:STATEMENT_BREAK");
  }

  // PY-3067
  public void testErrorOpenParInMethod() {
    doTest("""
             class C:
                 def f(self):
                     (

                 def g(self):
                     pass
             """,
           "Py:CLASS_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:COLON", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:INDENT", "Py:DEF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:LPAR", "Py:IDENTIFIER", "Py:RPAR", "Py:COLON", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:INDENT", "Py:LPAR", "Py:LINE_BREAK",
           "Py:STATEMENT_BREAK", "Py:DEDENT", "Py:LINE_BREAK", // Error recovery
           "Py:DEF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:LPAR", "Py:IDENTIFIER", "Py:RPAR", "Py:COLON", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:INDENT", "Py:PASS_KEYWORD", "Py:STATEMENT_BREAK", "Py:DEDENT", "Py:DEDENT", "Py:LINE_BREAK",
           "Py:STATEMENT_BREAK");
  }

  // PY-6722
  public void testBackslashAndEmptyLineInsideSquareBrackets() {
    doTest("""
             xs = [ \\

             ]
             print(xs)
             """,
           "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:LBRACKET", "Py:SPACE", "Py:LINE_BREAK",
           "Py:LINE_BREAK",
           "Py:RBRACKET", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:IDENTIFIER", "Py:LPAR", "Py:IDENTIFIER", "Py:RPAR", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:STATEMENT_BREAK");
  }

  // PY-6722
  public void testSingleBackslashLineInsideSquareBrackets() {
    doTest("""
             xs = [
             \\
             ]
             """,
           "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:LBRACKET", "Py:LINE_BREAK",
           "Py:SPACE", "Py:LINE_BREAK",
           "Py:RBRACKET", "Py:STATEMENT_BREAK", "Py:LINE_BREAK",
           "Py:STATEMENT_BREAK");
  }

  // PY-18973
  public void testUnderscoresInHexInteger() {
    doTest("0xCAFE_F00D", "Py:INTEGER_LITERAL", "Py:STATEMENT_BREAK");
  }

  // PY-18973
  public void testUnderscoresInOctInteger() {
    doTest("0o1_23","Py:INTEGER_LITERAL", "Py:STATEMENT_BREAK");
    doTest("01_23", "Py:INTEGER_LITERAL", "Py:STATEMENT_BREAK");
  }

  // PY-18973
  public void testUnderscoresInBinInteger() {
    doTest("0b_0011_1111_0100_1110", "Py:INTEGER_LITERAL", "Py:STATEMENT_BREAK");
  }

  // PY-18973
  public void testUnderscoresInDecimalInteger() {
    doTest("10_000_000", "Py:INTEGER_LITERAL", "Py:STATEMENT_BREAK");
  }

  // PY-18973
  public void testUnderscoresInPointFloat() {
    doTest("10_00.00_23", "Py:FLOAT_LITERAL", "Py:STATEMENT_BREAK");
    doTest("10_00.", "Py:FLOAT_LITERAL", "Py:STATEMENT_BREAK");
  }

  // PY-18973
  public void testUnderscoresInExponentFloat() {
    doTest("10_00.00_23e1_2", "Py:FLOAT_LITERAL", "Py:STATEMENT_BREAK");
    doTest("10_00.00_23E1_2", "Py:FLOAT_LITERAL", "Py:STATEMENT_BREAK");
    doTest("10_00.e1_2", "Py:FLOAT_LITERAL", "Py:STATEMENT_BREAK");
    doTest("10_00.E1_2", "Py:FLOAT_LITERAL", "Py:STATEMENT_BREAK");

    doTest("10_00.00_23e+1_2", "Py:FLOAT_LITERAL", "Py:STATEMENT_BREAK");
    doTest("10_00.00_23E+1_2", "Py:FLOAT_LITERAL", "Py:STATEMENT_BREAK");
    doTest("10_00.e+1_2", "Py:FLOAT_LITERAL", "Py:STATEMENT_BREAK");
    doTest("10_00.E+1_2", "Py:FLOAT_LITERAL", "Py:STATEMENT_BREAK");

    doTest("10_00.00_23e-1_2", "Py:FLOAT_LITERAL", "Py:STATEMENT_BREAK");
    doTest("10_00.00_23E-1_2", "Py:FLOAT_LITERAL", "Py:STATEMENT_BREAK");
    doTest("10_00.e-1_2", "Py:FLOAT_LITERAL", "Py:STATEMENT_BREAK");
    doTest("10_00.E-1_2", "Py:FLOAT_LITERAL", "Py:STATEMENT_BREAK");


    doTest("10_0000_23e1_2", "Py:FLOAT_LITERAL", "Py:STATEMENT_BREAK");
    doTest("10_0000_23E1_2", "Py:FLOAT_LITERAL", "Py:STATEMENT_BREAK");
    doTest("10_00e1_2", "Py:FLOAT_LITERAL", "Py:STATEMENT_BREAK");
    doTest("10_00E1_2", "Py:FLOAT_LITERAL", "Py:STATEMENT_BREAK");

    doTest("10_0000_23e+1_2", "Py:FLOAT_LITERAL", "Py:STATEMENT_BREAK");
    doTest("10_0000_23E+1_2", "Py:FLOAT_LITERAL", "Py:STATEMENT_BREAK");
    doTest("10_00e+1_2", "Py:FLOAT_LITERAL", "Py:STATEMENT_BREAK");
    doTest("10_00E+1_2", "Py:FLOAT_LITERAL", "Py:STATEMENT_BREAK");

    doTest("10_0000_23e-1_2", "Py:FLOAT_LITERAL", "Py:STATEMENT_BREAK");
    doTest("10_0000_23E-1_2", "Py:FLOAT_LITERAL", "Py:STATEMENT_BREAK");
    doTest("10_00e-1_2", "Py:FLOAT_LITERAL", "Py:STATEMENT_BREAK");
    doTest("10_00E-1_2", "Py:FLOAT_LITERAL", "Py:STATEMENT_BREAK");
  }

  // PY-18973
  public void testUnderscoresInImagNumber() {
    doTest("10_00.00_23j", "Py:IMAGINARY_LITERAL", "Py:STATEMENT_BREAK");
    doTest("10_00.00_23J", "Py:IMAGINARY_LITERAL", "Py:STATEMENT_BREAK");

    doTest("10_00.00_23e1_2j", "Py:IMAGINARY_LITERAL", "Py:STATEMENT_BREAK");
    doTest("10_00.00_23e1_2J", "Py:IMAGINARY_LITERAL", "Py:STATEMENT_BREAK");

    doTest("10_000_000j", "Py:IMAGINARY_LITERAL", "Py:STATEMENT_BREAK");
    doTest("10_000_000J", "Py:IMAGINARY_LITERAL", "Py:STATEMENT_BREAK");
  }


  public void testFStringMatchingQuoteRecoveryInsideContentOfNestedStringLiteral() {
    doTest("s = f'{ur\"foo'bar\"}'", 
           "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", 
           "Py:FSTRING_START", "Py:FSTRING_FRAGMENT_START", "Py:SINGLE_QUOTED_STRING", "Py:FSTRING_END", 
           "Py:IDENTIFIER", "Py:SINGLE_QUOTED_STRING", "Py:STATEMENT_BREAK");
  }

  public void testFStringMatchingQuoteRecoveryQuoteOfNestedStringLiteralWithPrefix() {
    doTest("s = f'{ur'foo'}'",
           "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", 
           "Py:FSTRING_START", "Py:FSTRING_FRAGMENT_START", "Py:IDENTIFIER", "Py:FSTRING_END", 
           "Py:IDENTIFIER", "Py:SINGLE_QUOTED_STRING", "Py:STATEMENT_BREAK");
  }

  public void testFStringMatchingQuoteRecoveryQuoteOfNestedStringLiteralWithoutPrefix() {
    doTest("s = f'{'foo'}'",
           "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:FSTRING_START", "Py:FSTRING_FRAGMENT_START", "Py:FSTRING_END",
           "Py:IDENTIFIER", "Py:SINGLE_QUOTED_STRING", "Py:STATEMENT_BREAK");
  }

  public void testNoStatementBreakInsideFragmentOfMultilineFString() {
    doTest("s = f'''{1 + \n" +
           "2}'''",
           "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", 
           "Py:FSTRING_START", "Py:FSTRING_FRAGMENT_START", "Py:INTEGER_LITERAL", "Py:SPACE", "Py:PLUS", "Py:LINE_BREAK", 
           "Py:INTEGER_LITERAL", "Py:FSTRING_FRAGMENT_END", "Py:FSTRING_END", "Py:STATEMENT_BREAK");
  }

  public void testStatementBreakInsideFragmentOfSingleLineFString() {
    doTest("s = f'{1 +\n" +
           "    2}'",
           "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", 
           "Py:FSTRING_START", "Py:FSTRING_FRAGMENT_START", "Py:INTEGER_LITERAL", "Py:SPACE", "Py:PLUS", "Py:STATEMENT_BREAK", 
           "Py:LINE_BREAK", "Py:INDENT", "Py:INTEGER_LITERAL", "Py:RBRACE", "Py:SINGLE_QUOTED_STRING", "Py:STATEMENT_BREAK");
  }

  public void testFStringUnmatchedQuotesAsTextParts() {
    doTest("s = f'foo\"bar'", 
           "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", 
           "Py:FSTRING_START", "Py:FSTRING_TEXT", "Py:FSTRING_END", "Py:STATEMENT_BREAK");
  }

  public void testFStringUnmatchedLineBreaksAsTextParts() {
    doTest("s = f'''foo\n" +
           "bar'''",
           "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", 
           "Py:FSTRING_START", "Py:FSTRING_TEXT", "Py:FSTRING_END", "Py:STATEMENT_BREAK");
  }

  public void testFStringNamedUnicodeEscapes() {
    doTest("s = f'\\N{LATIN SMALL LETTER A}'", 
           "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", 
           "Py:FSTRING_START", "Py:FSTRING_TEXT", "Py:FSTRING_END", "Py:STATEMENT_BREAK");
    doTest("s = f'\\N{LATIN SMALL LETTER A'",
           "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", 
           "Py:FSTRING_START", "Py:FSTRING_TEXT", "Py:FSTRING_END", "Py:STATEMENT_BREAK");
    doTest("s = f'\\N{'",
           "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", 
           "Py:FSTRING_START", "Py:FSTRING_TEXT", "Py:FSTRING_END", "Py:STATEMENT_BREAK");
  }

  public void testFStringBackslashEscapedBraces() {
    doTest("s = f'foo\\{x}'", 
           "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", 
           "Py:FSTRING_START", "Py:FSTRING_TEXT",
           "Py:FSTRING_FRAGMENT_START", "Py:IDENTIFIER", "Py:FSTRING_FRAGMENT_END", 
           "Py:FSTRING_END", "Py:STATEMENT_BREAK");
    doTest("s = f'{x:foo\\{y}bar\\}'", 
           "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", 
           "Py:FSTRING_START", "Py:FSTRING_FRAGMENT_START", "Py:IDENTIFIER", 
           "Py:FSTRING_FRAGMENT_FORMAT_START", "Py:FSTRING_TEXT",
           "Py:FSTRING_FRAGMENT_START", "Py:IDENTIFIER", "Py:FSTRING_FRAGMENT_END", 
           "Py:FSTRING_TEXT",
           "Py:FSTRING_FRAGMENT_END", "Py:FSTRING_END", "Py:STATEMENT_BREAK");
  }

  // PY-21697
  public void testTripleSingleQuotedStringWithEscapedSlashAfterOneQuote() {
    doTest("""
             s = '''
             '\\\\'''
             '''
             """,
           "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:TRIPLE_QUOTED_STRING",
           "Py:STATEMENT_BREAK", "Py:LINE_BREAK", "Py:TRIPLE_QUOTED_STRING", "Py:STATEMENT_BREAK");
  }
  
  // PY-21697
  public void testTripleSingleQuotedStringWithEscapedSlashAfterTwoQuotes() {
    doTest("""
             s = '''
             ''\\\\'''
             '''
             """,
           "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:TRIPLE_QUOTED_STRING",
           "Py:STATEMENT_BREAK", "Py:LINE_BREAK", "Py:TRIPLE_QUOTED_STRING", "Py:STATEMENT_BREAK");
  }
  
  // PY-21697
  public void testTripleDoubleQuotedStringWithEscapedSlashAfterOneQuote() {
        doTest("""
                 s = ""\"
                 "\\\\""\"
                 ""\"
                 """,
               "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:TRIPLE_QUOTED_STRING",
               "Py:STATEMENT_BREAK", "Py:LINE_BREAK", "Py:TRIPLE_QUOTED_STRING", "Py:STATEMENT_BREAK");
  }
  
  // PY-21697
  public void testTripleDoubleQuotedStringWithEscapedSlashAfterTwoQuotes() {
        doTest("""
                 s = ""\"
                 ""\\\\""\"
                 ""\"
                 """,
               "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:TRIPLE_QUOTED_STRING",
               "Py:STATEMENT_BREAK", "Py:LINE_BREAK", "Py:TRIPLE_QUOTED_STRING", "Py:STATEMENT_BREAK");
  }

  // PY-40757
  public void testVerticalTab() {
    doTest("\u000Bimport math",
           "BAD_CHARACTER", "Py:IMPORT_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:STATEMENT_BREAK");
  }

  // PY-40757
  public void testVerticalTabAfterComment() {
    doTest("# comment\n" +
           "\u000Bimport math",
           "Py:END_OF_LINE_COMMENT", "Py:LINE_BREAK",
           "BAD_CHARACTER", "Py:IMPORT_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:STATEMENT_BREAK");
  }

  private static void doTest(String text, String... expectedTokens) {
    PyLexerTestCase.doLexerTest(text, new PythonIndentingLexer(), expectedTokens);
  }
}
