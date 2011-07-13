package com.jetbrains.python;

import com.jetbrains.python.fixtures.PyLexerTestCase;
import com.jetbrains.python.lexer.PyStringLiteralLexer;

/**
 * @author yole
 */
public class PyStringLiteralLexerTest extends PyLexerTestCase {
  public void testBackslashN() {  // PY-1313
    doLexerTest("u\"\\N{LATIN SMALL LETTER B}\"", new PyStringLiteralLexer(PyElementTypes.STRING_LITERAL_EXPRESSION),
                "Py:STRING_LITERAL_EXPRESSION", "VALID_STRING_ESCAPE_TOKEN", "Py:STRING_LITERAL_EXPRESSION");    
  }
}
