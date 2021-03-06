// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.jetbrains.python.fixtures.PyLexerTestCase;
import com.jetbrains.python.lexer.PyFStringLiteralLexer;
import com.jetbrains.python.lexer.PyStringLiteralLexer;

/**
 * @author yole
 */
public class PyStringLiteralLexerTest extends PyLexerTestCase {
  public void testBackslashN() {  // PY-1313
    PyLexerTestCase.doLexerTest("u\"\\N{LATIN SMALL LETTER B}\"", new PyStringLiteralLexer(PyTokenTypes.SINGLE_QUOTED_UNICODE),
                                "Py:SINGLE_QUOTED_UNICODE", "VALID_STRING_ESCAPE_TOKEN", "Py:SINGLE_QUOTED_UNICODE");
  }

  public void testRawBackslashN() {
    doLexerTest("r'[\\w\\']'", new PyStringLiteralLexer(PyTokenTypes.SINGLE_QUOTED_STRING), true,
                "r'[\\w\\']'");
  }

  // PY-20921
  public void testIllegalPrefixes() {
    doLexerTest("ff'foo'", new PyStringLiteralLexer(PyTokenTypes.SINGLE_QUOTED_UNICODE), "Py:SINGLE_QUOTED_UNICODE");
    doLexerTest("fff'foo'", new PyStringLiteralLexer(PyTokenTypes.SINGLE_QUOTED_UNICODE), "Py:SINGLE_QUOTED_UNICODE");
    doLexerTest("rrr'''foo'''", new PyStringLiteralLexer(PyTokenTypes.TRIPLE_QUOTED_UNICODE), "Py:TRIPLE_QUOTED_UNICODE");
  }

  // PY-21399
  public void testBackslashBreaksAnyEscapeSequence() {
    doLexerTest("'\\u\\')", new PyStringLiteralLexer(PyTokenTypes.SINGLE_QUOTED_UNICODE), true, "'", "\\u", "\\'", ")");
    doLexerTest("'\\u\\')", new PyStringLiteralLexer(PyTokenTypes.SINGLE_QUOTED_UNICODE),
                "Py:SINGLE_QUOTED_UNICODE", "INVALID_UNICODE_ESCAPE_TOKEN", "VALID_STRING_ESCAPE_TOKEN",
                "Py:SINGLE_QUOTED_UNICODE");

    doLexerTest("'\\x\\')", new PyStringLiteralLexer(PyTokenTypes.SINGLE_QUOTED_UNICODE), true, "'", "\\x", "\\'", ")");
    doLexerTest("'\\x\\')", new PyStringLiteralLexer(PyTokenTypes.SINGLE_QUOTED_UNICODE),
                "Py:SINGLE_QUOTED_UNICODE", "INVALID_UNICODE_ESCAPE_TOKEN", "VALID_STRING_ESCAPE_TOKEN",
                "Py:SINGLE_QUOTED_UNICODE");

    doLexerTest("'\\N{F\\')", new PyStringLiteralLexer(PyTokenTypes.SINGLE_QUOTED_UNICODE), true, "'", "\\N{F", "\\'", ")");
    doLexerTest("'\\N{F\\')", new PyStringLiteralLexer(PyTokenTypes.SINGLE_QUOTED_UNICODE),
                "Py:SINGLE_QUOTED_UNICODE", "INVALID_UNICODE_ESCAPE_TOKEN", "VALID_STRING_ESCAPE_TOKEN",
                "Py:SINGLE_QUOTED_UNICODE");
  }

  // PY-40863
  public void testFStringDoubleCurleyBrace() {
    doLexerTest("a{{b", new PyFStringLiteralLexer(PyTokenTypes.FSTRING_TEXT), true, "a", "{{", "b");
    doLexerTest("a\\}}b", new PyFStringLiteralLexer(PyTokenTypes.FSTRING_RAW_TEXT), true, "a", "\\", "}}", "b");
  }
}
