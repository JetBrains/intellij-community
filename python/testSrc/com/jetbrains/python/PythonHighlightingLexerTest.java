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
import com.jetbrains.python.lexer.PythonHighlightingLexer;
import com.jetbrains.python.psi.LanguageLevel;

/**
 * user : catherine
 */
public class PythonHighlightingLexerTest extends PyLexerTestCase {

  public void testFromFutureUnicode() {
    doTest(LanguageLevel.PYTHON26, "from __future__ import unicode_literals\n\n" +
                                   "s = \"some string\"",
           "Py:FROM_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:SPACE", "Py:IMPORT_KEYWORD", "Py:SPACE", "Py:IDENTIFIER",
           "Py:LINE_BREAK", "Py:LINE_BREAK", "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:SINGLE_QUOTED_UNICODE");
  }

  public void testFromFutureUnicodeDocstring() {
    doTest(LanguageLevel.PYTHON26, "\"\"\"docstring\"\"\"\n" +
                                   "from __future__ import unicode_literals\n\n" +
                                   "s = \"some string\"",
           "Py:DOCSTRING", "Py:LINE_BREAK",
           "Py:FROM_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:SPACE", "Py:IMPORT_KEYWORD", "Py:SPACE", "Py:IDENTIFIER",
           "Py:LINE_BREAK", "Py:LINE_BREAK", "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:SINGLE_QUOTED_UNICODE");
  }

  public void testFromFutureUnicodeWithBackslash() {
    doTest(LanguageLevel.PYTHON26, "from __future__ \\\nimport unicode_literals\n\n" +
                                   "s = \"some string\"",
           "Py:FROM_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:SPACE", "Py:BACKSLASH", "Py:LINE_BREAK",
           "Py:IMPORT_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:LINE_BREAK",
           "Py:LINE_BREAK", "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:SINGLE_QUOTED_UNICODE");
  }

  public void testFromFutureUnicodeWithBrace() {
    doTest(LanguageLevel.PYTHON26, "from __future__ import (unicode_literals)\n\n" +
                                   "s = \"some string\"",
           "Py:FROM_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:SPACE",
           "Py:IMPORT_KEYWORD", "Py:SPACE", "Py:LPAR", "Py:IDENTIFIER", "Py:RPAR", "Py:LINE_BREAK",
           "Py:LINE_BREAK", "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:SINGLE_QUOTED_UNICODE");
  }

  public void testFromFutureUnicodeComment() {
    doTest(LanguageLevel.PYTHON26, "#one comment\n" +
                                   "from __future__ import ((unicode_literals))\n" +
                                   "s = \"some string\"",
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
    doTest(LanguageLevel.PYTHON26, "from __future__ import absolute_import\n" +
                                   "from __future__ import (unicode_literals)\n\n" +
                                   "s = \"some string\"",
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
    doTest(LanguageLevel.PYTHON26, "a = 2\n" +
                                   "from __future__ import unicode_literals\n\n" +
                                   "s = \"some string\"",
           "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE","Py:INTEGER_LITERAL", "Py:LINE_BREAK",
           "Py:FROM_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:SPACE", "Py:IMPORT_KEYWORD", "Py:SPACE", "Py:IDENTIFIER",
           "Py:LINE_BREAK", "Py:LINE_BREAK", "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:SINGLE_QUOTED_STRING");
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

  public void testSingleDocStringWithBackslash() {
    doTest(LanguageLevel.PYTHON27, "\"one docstring \" \\\n\"new line of docstring\"\n",
                      "Py:DOCSTRING", "Py:SPACE", "Py:BACKSLASH", "Py:LINE_BREAK", "Py:DOCSTRING", "Py:LINE_BREAK");
  }

  public void testSingleDocstringFunction() {
    doTest(LanguageLevel.PYTHON27, "def foo():\n" +
                                   "  \"\"\"function foo\"\"\"\n" +
                                   "  a = \"string\"",
                      "Py:DEF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:LPAR", "Py:RPAR", "Py:COLON", "Py:LINE_BREAK",
                      "Py:SPACE", "Py:SPACE", "Py:DOCSTRING", "Py:LINE_BREAK", "Py:SPACE", "Py:SPACE", "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE",
                      "Py:SINGLE_QUOTED_STRING");
  }

  public void testNotDocstring() { // PY-4481
      doTest(LanguageLevel.PYTHON27, "d = {\n" +
                                     " 'abc': 'def',\n" +
                                     " 'ghi': 'jkl'\n" +
                                     " }",
             "Py:IDENTIFIER", "Py:SPACE", "Py:EQ", "Py:SPACE", "Py:LBRACE", "Py:LINE_BREAK", "Py:SPACE", "Py:SINGLE_QUOTED_STRING",
             "Py:COLON", "Py:SPACE", "Py:SINGLE_QUOTED_STRING", "Py:COMMA", "Py:LINE_BREAK", "Py:SPACE", "Py:SINGLE_QUOTED_STRING",
             "Py:COLON", "Py:SPACE", "Py:SINGLE_QUOTED_STRING", "Py:LINE_BREAK", "Py:SPACE", "Py:RBRACE");
    }

  private static void doTest(LanguageLevel languageLevel, String text, String... expectedTokens) {
    PyLexerTestCase.doLexerTest(text, new PythonHighlightingLexer(languageLevel), expectedTokens);
  }
}
