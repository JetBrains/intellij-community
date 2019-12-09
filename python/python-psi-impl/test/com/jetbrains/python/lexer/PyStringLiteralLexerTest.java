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
package com.jetbrains.python.lexer;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.fixtures.PyLexerTestCase;

/**
 * @author yole
 */
public class PyStringLiteralLexerTest extends PyLexerTestCase {
  public void testBackslashN() {  // PY-1313
    PyLexerTestCase.doLexerTest("u\"\\N{LATIN SMALL LETTER B}\"", new PyStringLiteralLexer(PyTokenTypes.SINGLE_QUOTED_UNICODE),
                                "Py:SINGLE_QUOTED_UNICODE", "VALID_STRING_ESCAPE_TOKEN", "Py:SINGLE_QUOTED_UNICODE");
  }

  public void testRawBackslashN() {
    PyLexerTestCase.doLexerTest("r'[\\w\\']'", new PyStringLiteralLexer(PyTokenTypes.SINGLE_QUOTED_STRING), true,
                                "r'[\\w\\']'");
  }

  // PY-20921
  public void testIllegalPrefixes() {
    PyLexerTestCase.doLexerTest("ff'foo'", new PyStringLiteralLexer(PyTokenTypes.SINGLE_QUOTED_UNICODE), "Py:SINGLE_QUOTED_UNICODE");
    PyLexerTestCase.doLexerTest("fff'foo'", new PyStringLiteralLexer(PyTokenTypes.SINGLE_QUOTED_UNICODE), "Py:SINGLE_QUOTED_UNICODE");
    PyLexerTestCase.doLexerTest("rrr'''foo'''", new PyStringLiteralLexer(PyTokenTypes.TRIPLE_QUOTED_UNICODE), "Py:TRIPLE_QUOTED_UNICODE");
  }

  // PY-21399
  public void testBackslashBreaksAnyEscapeSequence() {
    PyLexerTestCase.doLexerTest("'\\u\\')", new PyStringLiteralLexer(PyTokenTypes.SINGLE_QUOTED_UNICODE), true, "'", "\\u", "\\'", ")");
    PyLexerTestCase.doLexerTest("'\\u\\')", new PyStringLiteralLexer(PyTokenTypes.SINGLE_QUOTED_UNICODE),
                                "Py:SINGLE_QUOTED_UNICODE", "INVALID_UNICODE_ESCAPE_TOKEN", "VALID_STRING_ESCAPE_TOKEN",
                                "Py:SINGLE_QUOTED_UNICODE");

    PyLexerTestCase.doLexerTest("'\\x\\')", new PyStringLiteralLexer(PyTokenTypes.SINGLE_QUOTED_UNICODE), true, "'", "\\x", "\\'", ")");
    PyLexerTestCase.doLexerTest("'\\x\\')", new PyStringLiteralLexer(PyTokenTypes.SINGLE_QUOTED_UNICODE),
                                "Py:SINGLE_QUOTED_UNICODE", "INVALID_UNICODE_ESCAPE_TOKEN", "VALID_STRING_ESCAPE_TOKEN",
                                "Py:SINGLE_QUOTED_UNICODE");

    PyLexerTestCase.doLexerTest("'\\N{F\\')", new PyStringLiteralLexer(PyTokenTypes.SINGLE_QUOTED_UNICODE), true, "'", "\\N{F", "\\'", ")");
    PyLexerTestCase.doLexerTest("'\\N{F\\')", new PyStringLiteralLexer(PyTokenTypes.SINGLE_QUOTED_UNICODE),
                                "Py:SINGLE_QUOTED_UNICODE", "INVALID_UNICODE_ESCAPE_TOKEN", "VALID_STRING_ESCAPE_TOKEN",
                                "Py:SINGLE_QUOTED_UNICODE");
  }
}
