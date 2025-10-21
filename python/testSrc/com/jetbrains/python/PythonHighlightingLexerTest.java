/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.jetbrains.python.fixtures.PyLexerTestCase;
import com.jetbrains.python.highlighting.PyHighlighter;
import com.jetbrains.python.lexer.PythonHighlightingLexer;
import com.jetbrains.python.psi.LanguageLevel;

import static com.jetbrains.python.fixtures.PyTestCase.fixme;

/**
 * user : catherine
 */
public class PythonHighlightingLexerTest extends PyLexerTestCase {

  public void testFromFutureUnicode() {
    doTest(LanguageLevel.PYTHON26, """
             from __future__ import unicode_literals

             s = "some string\"""",
           "Py:FROM_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:SPACE", "Py:IMPORT_KEYWORD", "Py:SPACE", "Py:IDENTIFIER",
           "Py:LINE_BREAK", "Py:LINE_BREAK", "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:SINGLE_QUOTED_UNICODE");
  }

  public void testFromFutureUnicodeDocstring() {
    doTest(LanguageLevel.PYTHON26, """
             ""\"docstring""\"
             from __future__ import unicode_literals

             s = "some string\"""",
           "Py:DOCSTRING", "Py:LINE_BREAK",
           "Py:FROM_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:SPACE", "Py:IMPORT_KEYWORD", "Py:SPACE", "Py:IDENTIFIER",
           "Py:LINE_BREAK", "Py:LINE_BREAK", "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:SINGLE_QUOTED_UNICODE");
  }

  public void testFromFutureUnicodeWithBackslash() {
    doTest(LanguageLevel.PYTHON26, """
             from __future__ \\
             import unicode_literals

             s = "some string\"""",
           "Py:FROM_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:SPACE", "Py:BACKSLASH", "Py:LINE_BREAK",
           "Py:IMPORT_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:LINE_BREAK",
           "Py:LINE_BREAK", "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:SINGLE_QUOTED_UNICODE");
  }

  public void testFromFutureUnicodeWithBrace() {
    doTest(LanguageLevel.PYTHON26, """
             from __future__ import (unicode_literals)

             s = "some string\"""",
           "Py:FROM_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:SPACE",
           "Py:IMPORT_KEYWORD", "Py:SPACE", "Py:LPAR", "Py:IDENTIFIER", "Py:RPAR", "Py:LINE_BREAK",
           "Py:LINE_BREAK", "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:SINGLE_QUOTED_UNICODE");
  }

  public void testFromFutureUnicodeComment() {
    doTest(LanguageLevel.PYTHON26, """
             #one comment
             from __future__ import ((unicode_literals))
             s = "some string\"""",
           "Py:END_OF_LINE_COMMENT", "Py:LINE_BREAK", "Py:FROM_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:SPACE",
           "Py:IMPORT_KEYWORD", "Py:SPACE", "Py:LPAR", "Py:LPAR", "Py:IDENTIFIER", "Py:RPAR", "Py:RPAR",
           "Py:LINE_BREAK", "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:SINGLE_QUOTED_STRING");
  }

  public void testFromFutureBytes() {
    doTest(LanguageLevel.PYTHON26, "from __future__ import unicode_literals\n" +
                                   "s = b\"some string\"",
           "Py:FROM_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:SPACE",
           "Py:IMPORT_KEYWORD", "Py:SPACE", "Py:IDENTIFIER",
           "Py:LINE_BREAK", "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:SINGLE_QUOTED_STRING");
  }

  public void testBytes30() {
    doTest(LanguageLevel.PYTHON34, "s = b\"some string\"",
                            "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:SINGLE_QUOTED_STRING");
  }

  public void testBytes() {
    doTest(LanguageLevel.PYTHON26, "s = b\"some string\"",
                            "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:SINGLE_QUOTED_STRING");
  }

  public void testFromFutureUnicodeWithBraceFail() {
    doTest(LanguageLevel.PYTHON26, "from __future__ import ((unicode_literals))\n" +
                                   "s = \"some string\"",
           "Py:FROM_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:SPACE", "Py:IMPORT_KEYWORD", "Py:SPACE",
           "Py:LPAR", "Py:LPAR", "Py:IDENTIFIER", "Py:RPAR", "Py:RPAR",
           "Py:LINE_BREAK", "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:SINGLE_QUOTED_STRING");
  }

  public void testDoubleFromFutureUnicode() {
    doTest(LanguageLevel.PYTHON26, """
             from __future__ import absolute_import
             from __future__ import (unicode_literals)

             s = "some string\"""",
           "Py:FROM_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:SPACE", "Py:IMPORT_KEYWORD", "Py:SPACE", "Py:IDENTIFIER",
           "Py:LINE_BREAK", "Py:FROM_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:SPACE",
           "Py:IMPORT_KEYWORD", "Py:SPACE", "Py:LPAR", "Py:IDENTIFIER", "Py:RPAR", "Py:LINE_BREAK",
           "Py:LINE_BREAK", "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:SINGLE_QUOTED_UNICODE");
  }

  public void testDoubleFromFutureUnicodeWithComma() {
    doTest(LanguageLevel.PYTHON26, "from __future__ import absolute_import, unicode_literals\n" +
                                   "s = \"some string\"",
           "Py:FROM_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:SPACE", "Py:IMPORT_KEYWORD", "Py:SPACE", "Py:IDENTIFIER",
           "Py:COMMA", "Py:SPACE", "Py:IDENTIFIER",
           "Py:LINE_BREAK", "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:SINGLE_QUOTED_UNICODE");
  }

  public void testDoubleFromFutureUnicodeWithCommaFail() {
    doTest(LanguageLevel.PYTHON26, "from __future__ import absolute_import, (unicode_literals)\n" +
                                   "s = \"some string\"",
           "Py:FROM_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:SPACE", "Py:IMPORT_KEYWORD", "Py:SPACE", "Py:IDENTIFIER",
           "Py:COMMA", "Py:SPACE", "Py:LPAR", "Py:IDENTIFIER", "Py:RPAR",
           "Py:LINE_BREAK", "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:SINGLE_QUOTED_STRING");
  }

  public void testFromFutureUnicodeFail() {
    doTest(LanguageLevel.PYTHON26, """
             a = 2
             from __future__ import unicode_literals

             s = "some string\"""",
           "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:INTEGER_LITERAL", "Py:LINE_BREAK",
           "Py:FROM_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:SPACE", "Py:IMPORT_KEYWORD", "Py:SPACE", "Py:IDENTIFIER",
           "Py:LINE_BREAK", "Py:LINE_BREAK", "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:SINGLE_QUOTED_STRING");
  }

  public void testFromFuturePrint() {
    doTest(LanguageLevel.PYTHON27, "from __future__ import print_function\n" +
                                   "print(1)",
           "Py:FROM_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:SPACE", "Py:IMPORT_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:LINE_BREAK",
           "Py:IDENTIFIER", "Py:LPAR", "Py:INTEGER_LITERAL", "Py:RPAR");
  }

  public void testWithoutFromFuturePrint() {
    doTest(LanguageLevel.PYTHON27, "print(1)", "Py:PRINT_KEYWORD", "Py:LPAR", "Py:INTEGER_LITERAL", "Py:RPAR");
  }

  public void testFromFuturePrintAndUnicode() {
    doTest(LanguageLevel.PYTHON27, "from __future__ import unicode_literals, print_function\n" +
                                   "print(\"some string\")",
           "Py:FROM_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:SPACE", "Py:IMPORT_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:COMMA", "Py:SPACE", "Py:IDENTIFIER", "Py:LINE_BREAK",
           "Py:IDENTIFIER", "Py:LPAR", "Py:SINGLE_QUOTED_UNICODE", "Py:RPAR");
  }

  public void testFromFuturePrintNotFirstFail() {
    doTest(LanguageLevel.PYTHON27, """
             a = 2
             from __future__ import print_function
             print(1)""",
           "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:INTEGER_LITERAL", "Py:LINE_BREAK",
           "Py:FROM_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:SPACE", "Py:IMPORT_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:LINE_BREAK",
           "Py:PRINT_KEYWORD", "Py:LPAR", "Py:INTEGER_LITERAL", "Py:RPAR");
  }

  public void testUnicode30() {
    doTest(LanguageLevel.PYTHON34, "s = \"some string\"",
                      "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:SINGLE_QUOTED_UNICODE");
  }

  public void testString() {
    doTest(LanguageLevel.PYTHON27, "s = \"some string\"",
                      "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:SINGLE_QUOTED_STRING");
  }

  public void testUnicode() {
    doTest(LanguageLevel.PYTHON27, "s = u\"some string\"",
                      "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:SINGLE_QUOTED_UNICODE");
  }

  public void testDocString() {
    doTest(LanguageLevel.PYTHON27, "\"\"\"one docstrings \"\"\"\n",
                      "Py:DOCSTRING", "Py:LINE_BREAK");
  }

  public void testMetaClass() {
    doTest(LanguageLevel.getLatest(), """
               class IOBase(metaclass=abc.ABCMeta):
                 pass""",
           "Py:CLASS_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:LPAR", "Py:IDENTIFIER", "Py:EQ", "Py:IDENTIFIER", "Py:DOT", "Py:IDENTIFIER", "Py:RPAR", "Py:COLON", "Py:LINE_BREAK",
           "Py:SPACE", "Py:SPACE", "Py:PASS_KEYWORD");
  }

  public void testSingleDocStringWithBackslash() {
    doTest(LanguageLevel.PYTHON27, "\"one docstring \" \\\n\"new line of docstring\"\n",
                      "Py:DOCSTRING", "Py:SPACE", "Py:BACKSLASH", "Py:LINE_BREAK", "Py:DOCSTRING", "Py:LINE_BREAK");
  }

  public void testSingleDocstringFunction() {
    doTest(LanguageLevel.PYTHON27, """
             def foo():
               ""\"function foo""\"
               a = "string\"""",
           "Py:DEF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:LPAR", "Py:RPAR", "Py:COLON", "Py:LINE_BREAK",
           "Py:SPACE", "Py:SPACE", "Py:DOCSTRING", "Py:LINE_BREAK", "Py:SPACE", "Py:SPACE", "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE",
           "Py:SINGLE_QUOTED_STRING");
  }

  public void testNotDocstring() { // PY-4481
      doTest(LanguageLevel.PYTHON27, """
               d = {
                'abc': 'def',
                'ghi': 'jkl'
                }""",
             "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:LBRACE", "Py:LINE_BREAK", "Py:SPACE", "Py:SINGLE_QUOTED_STRING",
             "Py:COLON", "Py:SPACE", "Py:SINGLE_QUOTED_STRING", "Py:COMMA", "Py:LINE_BREAK", "Py:SPACE", "Py:SINGLE_QUOTED_STRING",
             "Py:COLON", "Py:SPACE", "Py:SINGLE_QUOTED_STRING", "Py:LINE_BREAK", "Py:SPACE", "Py:RBRACE");
    }

  public void testDocstringAtModule() {
    doTest(LanguageLevel.getLatest(), """
             ""\" module docstring ""\"
             """,
           "Py:DOCSTRING", "Py:LINE_BREAK");
  }

  public void testDocstringAtModuleWithTrailingComment() {
    doTest(LanguageLevel.getLatest(), """
             ""\" module docstring ""\" # trailing comment
             """,
           "Py:DOCSTRING", "Py:SPACE", "Py:END_OF_LINE_COMMENT", "Py:LINE_BREAK");
  }

  public void testDocstringAtClass() {
    doTest(LanguageLevel.getLatest(), """
             class C:
                 ""\" class docstring ""\"
             """,
           "Py:CLASS_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:COLON", "Py:LINE_BREAK",
           "Py:SPACE", "Py:SPACE", "Py:SPACE", "Py:SPACE", "Py:DOCSTRING", "Py:LINE_BREAK");
  }

  public void testDocstringAtClassWithTrailingComment() {
    doTest(LanguageLevel.getLatest(), """
             class C:
                 ""\" class docstring ""\" # trailing comment
             """,
           "Py:CLASS_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:COLON", "Py:LINE_BREAK",
           "Py:SPACE", "Py:SPACE", "Py:SPACE", "Py:SPACE", "Py:DOCSTRING", "Py:SPACE", "Py:END_OF_LINE_COMMENT", "Py:LINE_BREAK");
  }

  public void testDocstringAtFunction() {
    doTest(LanguageLevel.getLatest(), """
             def fun():
                 ""\" function docstring ""\"
             """,
           "Py:DEF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:LPAR", "Py:RPAR", "Py:COLON", "Py:LINE_BREAK",
           "Py:SPACE", "Py:SPACE", "Py:SPACE", "Py:SPACE", "Py:DOCSTRING", "Py:LINE_BREAK");
  }

  public void testDocstringAtFunctionWithTrailingComment() {
    doTest(LanguageLevel.getLatest(), """
             def fun():
                 ""\" function docstring ""\" # trailing comment
             """,
           "Py:DEF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:LPAR", "Py:RPAR", "Py:COLON", "Py:LINE_BREAK",
           "Py:SPACE", "Py:SPACE", "Py:SPACE", "Py:SPACE", "Py:DOCSTRING", "Py:SPACE", "Py:END_OF_LINE_COMMENT", "Py:LINE_BREAK");
  }

  // PY-40634
  public void testDocstringAtVariableDeclaration() {
    fixme("PY-40634", AssertionError.class, () -> doTest(LanguageLevel.getLatest(), """
             VAR = 2
                 ""\" variable declaration docstring ""\"
             """,
           "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:INTEGER_LITERAL", "Py:LINE_BREAK",
           "Py:SPACE", "Py:SPACE", "Py:SPACE", "Py:SPACE", "Py:DOCSTRING", "Py:LINE_BREAK"));
  }

  // PY-40634
  public void testDocstringAtVariableDeclarationWithTrailingComment() {
    fixme("PY-40634", AssertionError.class, () -> doTest(LanguageLevel.getLatest(), """
             VAR = 2
                 ""\" variable declaration docstring ""\" # trailing comment
             """,
           "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:INTEGER_LITERAL", "Py:LINE_BREAK",
           "Py:SPACE", "Py:SPACE", "Py:SPACE", "Py:SPACE", "Py:DOCSTRING", "Py:SPACE", "Py:END_OF_LINE_COMMENT", "Py:LINE_BREAK"));
  }

  // PY-40634
  public void testDocstringAtClassVariableDeclaration() {
    fixme("PY-40634", AssertionError.class, () -> doTest(LanguageLevel.getLatest(), """
             class C:
               def __init__(self):
                   self.thing = 42
                   ""\" class variable declaration docstring ""\"
             """,
           "Py:CLASS_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:COLON", "Py:LINE_BREAK",
           "Py:SPACE", "Py:SPACE", "Py:DEF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:LPAR", "Py:IDENTIFIER", "Py:RPAR", "Py:COLON", "Py:LINE_BREAK",
           "Py:SPACE", "Py:SPACE", "Py:SPACE", "Py:SPACE", "Py:SPACE", "Py:SPACE", "Py:IDENTIFIER", "Py:DOT", "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:INTEGER_LITERAL", "Py:LINE_BREAK",
           "Py:SPACE", "Py:SPACE", "Py:SPACE", "Py:SPACE", "Py:SPACE", "Py:SPACE", "Py:DOCSTRING", "Py:LINE_BREAK"));
  }

  // PY-40634
  public void testDocstringAtClassVariableDeclarationWithTrailingComment() {
    fixme("PY-40634", AssertionError.class, () -> doTest(LanguageLevel.getLatest(), """
             class C:
               def __init__(self):
                   self.thing = 42
                   ""\" class variable declaration docstring ""\" # trailing comment
             """,
           "Py:CLASS_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:COLON", "Py:LINE_BREAK",
           "Py:SPACE", "Py:SPACE", "Py:DEF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:LPAR", "Py:IDENTIFIER", "Py:RPAR", "Py:COLON", "Py:LINE_BREAK",
           "Py:SPACE", "Py:SPACE", "Py:SPACE", "Py:SPACE", "Py:SPACE", "Py:SPACE", "Py:IDENTIFIER", "Py:DOT", "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:INTEGER_LITERAL", "Py:LINE_BREAK",
           "Py:SPACE", "Py:SPACE", "Py:SPACE", "Py:SPACE", "Py:SPACE", "Py:SPACE", "Py:DOCSTRING", "Py:SPACE", "Py:END_OF_LINE_COMMENT", "Py:LINE_BREAK"));
  }

  // PY-29665
  public void testRawBytesLiteral() {
    doTest(LanguageLevel.PYTHON27, "expr = br'raw bytes'", "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:SINGLE_QUOTED_STRING");
    doTest(LanguageLevel.PYTHON34, "expr = rb'raw bytes'", "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:SINGLE_QUOTED_STRING");
    doTest(LanguageLevel.PYTHON34, "expr = br'raw bytes'", "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:SINGLE_QUOTED_STRING");
  }

  // PY-31758
  public void testFStringEscapeSequences() {
    doTestStringHighlighting(LanguageLevel.PYTHON36, "f'foo\\nbar'",
                             "Py:FSTRING_START", "Py:FSTRING_TEXT", "VALID_STRING_ESCAPE_TOKEN", "Py:FSTRING_TEXT", "Py:FSTRING_END");
    doTestStringHighlighting(LanguageLevel.PYTHON36, "f'foo\\\nbar'",
                             "Py:FSTRING_START", "Py:FSTRING_TEXT", "VALID_STRING_ESCAPE_TOKEN", "Py:FSTRING_TEXT", "Py:FSTRING_END");
    doTestStringHighlighting(LanguageLevel.PYTHON36, "f'foo\\u0041bar'",
                             "Py:FSTRING_START", "Py:FSTRING_TEXT", "VALID_STRING_ESCAPE_TOKEN", "Py:FSTRING_TEXT", "Py:FSTRING_END");
    doTestStringHighlighting(LanguageLevel.PYTHON36, "f'foo\\x41bar'",
                             "Py:FSTRING_START", "Py:FSTRING_TEXT", "VALID_STRING_ESCAPE_TOKEN", "Py:FSTRING_TEXT", "Py:FSTRING_END");
    doTestStringHighlighting(LanguageLevel.PYTHON36, "f'foo\\101bar'",
                             "Py:FSTRING_START", "Py:FSTRING_TEXT", "VALID_STRING_ESCAPE_TOKEN", "Py:FSTRING_TEXT", "Py:FSTRING_END");
    doTestStringHighlighting(LanguageLevel.PYTHON36, "f'foo\\N{GREEK SMALL LETTER ALPHA}bar'",
                             "Py:FSTRING_START", "Py:FSTRING_TEXT", "VALID_STRING_ESCAPE_TOKEN", "Py:FSTRING_TEXT", "Py:FSTRING_END");

    doTestStringHighlighting(LanguageLevel.PYTHON36, "f'foo\\",
                             "Py:FSTRING_START", "Py:FSTRING_TEXT", "INVALID_CHARACTER_ESCAPE_TOKEN");

    doTestStringHighlighting(LanguageLevel.PYTHON36, "f'foo\\u00'",
                             "Py:FSTRING_START", "Py:FSTRING_TEXT", "INVALID_UNICODE_ESCAPE_TOKEN", "Py:FSTRING_END");
    doTestStringHighlighting(LanguageLevel.PYTHON36, "f'foo\\uZZZZbar'",
                             "Py:FSTRING_START", "Py:FSTRING_TEXT", "INVALID_UNICODE_ESCAPE_TOKEN", "Py:FSTRING_TEXT", "Py:FSTRING_END");

    doTestStringHighlighting(LanguageLevel.PYTHON36, "f'foo\\x0'",
                             "Py:FSTRING_START", "Py:FSTRING_TEXT", "INVALID_UNICODE_ESCAPE_TOKEN", "Py:FSTRING_END");
    doTestStringHighlighting(LanguageLevel.PYTHON36, "f'foo\\xZZbar'",
                             "Py:FSTRING_START", "Py:FSTRING_TEXT", "INVALID_UNICODE_ESCAPE_TOKEN", "Py:FSTRING_TEXT", "Py:FSTRING_END");

    doTestStringHighlighting(LanguageLevel.PYTHON36, "f'foo\\10'",
                             "Py:FSTRING_START", "Py:FSTRING_TEXT", "VALID_STRING_ESCAPE_TOKEN", "Py:FSTRING_END");
    doTestStringHighlighting(LanguageLevel.PYTHON36, "f'foo\\777'",
                             "Py:FSTRING_START", "Py:FSTRING_TEXT", "VALID_STRING_ESCAPE_TOKEN", "Py:FSTRING_TEXT", "Py:FSTRING_END");

    doTestStringHighlighting(LanguageLevel.PYTHON36, "f'foo\\N{GREEK SMALL LETTER ALPHA'",
                             "Py:FSTRING_START", "Py:FSTRING_TEXT", "INVALID_UNICODE_ESCAPE_TOKEN", "Py:FSTRING_END");

    doTestStringHighlighting(LanguageLevel.PYTHON36, "f'{x:\\n}'",
                             "Py:FSTRING_START", "Py:FSTRING_FRAGMENT_START", "Py:IDENTIFIER", "Py:FSTRING_FRAGMENT_FORMAT_START",
                             "VALID_STRING_ESCAPE_TOKEN", "Py:FSTRING_FRAGMENT_END", "Py:FSTRING_END");
  }

  // PY-32123
  public void testRawFStringEscapeSequences() {
    doTestStringHighlighting(LanguageLevel.PYTHON36, "rf'foo\\nbar'",
                             "Py:FSTRING_START", "Py:FSTRING_RAW_TEXT", "Py:FSTRING_RAW_TEXT", "Py:FSTRING_RAW_TEXT", "Py:FSTRING_END");
    doTestStringHighlighting(LanguageLevel.PYTHON36, "rf'foo\\\nbar'",
                             "Py:FSTRING_START", "Py:FSTRING_RAW_TEXT", "Py:FSTRING_RAW_TEXT", "Py:FSTRING_RAW_TEXT", "Py:FSTRING_END");
    doTestStringHighlighting(LanguageLevel.PYTHON36, "rf'foo\\",
                             "Py:FSTRING_START", "Py:FSTRING_RAW_TEXT", "Py:FSTRING_RAW_TEXT");
    doTestStringHighlighting(LanguageLevel.PYTHON36, "rf'{x:\\n}'",
                             "Py:FSTRING_START", "Py:FSTRING_FRAGMENT_START", "Py:IDENTIFIER", "Py:FSTRING_FRAGMENT_FORMAT_START",
                             "Py:FSTRING_RAW_TEXT", "Py:FSTRING_RAW_TEXT", "Py:FSTRING_FRAGMENT_END", "Py:FSTRING_END");
    doTestStringHighlighting(LanguageLevel.PYTHON36, "rf'{f\"\\n\"}'",
                             "Py:FSTRING_START", "Py:FSTRING_FRAGMENT_START",
                             "Py:FSTRING_START", "VALID_STRING_ESCAPE_TOKEN", "Py:FSTRING_END",
                             "Py:FSTRING_FRAGMENT_END", "Py:FSTRING_END");
    doTestStringHighlighting(LanguageLevel.PYTHON36, "f'{rf\"\\n\"}'",
                             "Py:FSTRING_START", "Py:FSTRING_FRAGMENT_START",
                             "Py:FSTRING_START", "Py:FSTRING_RAW_TEXT", "Py:FSTRING_RAW_TEXT", "Py:FSTRING_END",
                             "Py:FSTRING_FRAGMENT_END", "Py:FSTRING_END");
  }

  private static void doTest(LanguageLevel languageLevel, String text, String... expectedTokens) {
    PyLexerTestCase.doLexerTest(text, new PythonHighlightingLexer(languageLevel), expectedTokens);
  }

  private static void doTestStringHighlighting(LanguageLevel languageLevel, String text, String... expectedTokens) {
    PyLexerTestCase.doLexerTest(text, new PyHighlighter(languageLevel).getHighlightingLexer(), expectedTokens);
  }
}
