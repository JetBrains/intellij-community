package com.jetbrains.python;

import com.intellij.lexer.Lexer;
import com.jetbrains.python.documentation.doctest.PyDocstringParserDefinition;
import com.jetbrains.python.fixtures.PyTestCase;

/**
 * User: ktisha
 */
public class PyDocstringTest extends PyTestCase {

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/doctests/";
  }

  public void testWelcome() {
    doTestLexer("  >>> foo()", "Py:SPACE", "Py:INDENT", "Py:GTGT", "Py:SPACE", "Py:IDENTIFIER", "Py:LPAR", "Py:RPAR", "Py:STATEMENT_BREAK");
  }

  public void testDots() {
    doTestLexer(">>> grouped == { 2:2,\n" +
                "  ...              3:3}", "Py:GTGT","Py:SPACE", "Py:IDENTIFIER", "Py:SPACE", "Py:EQEQ", "Py:SPACE", "Py:LBRACE", "Py:SPACE", "Py:INTEGER_LITERAL", "Py:COLON", "Py:INTEGER_LITERAL", "Py:COMMA", "Py:LINE_BREAK", "Py:DOT", "Py:SPACE", "Py:SPACE", "Py:INTEGER_LITERAL", "Py:COLON", "Py:INTEGER_LITERAL", "Py:RBRACE", "Py:STATEMENT_BREAK");
  }

  public void testFunctionName() throws Throwable {
    doCompletionTest();
  }

  public void testClassName() throws Throwable {
    doCompletionTest();
  }

  public void doCompletionTest() throws Throwable {
    String inputDataFileName = getInputDataFileName(getTestName(true));
    String expectedResultFileName = getExpectedResultFileName(getTestName(true));
    myFixture.testCompletion(inputDataFileName, expectedResultFileName);
  }

  // util methods
  private static String getInputDataFileName(String testName) {
    return testName + ".docstring";
  }

  private static String getExpectedResultFileName(String testName) {
    return testName + ".expected.docstring";
  }


  private void doTestLexer(final String text, String... expectedTokens) {
    Lexer lexer = new PyDocstringParserDefinition().createLexer(myFixture.getProject());
    lexer.start(text);
    int idx = 0;
    while (lexer.getTokenType() != null) {
      if (idx >= expectedTokens.length) {
        StringBuilder remainingTokens = new StringBuilder("\"" + lexer.getTokenType().toString() + "\"");
        lexer.advance();
        while (lexer.getTokenType() != null) {
          remainingTokens.append(",");
          remainingTokens.append(" \"").append(lexer.getTokenType().toString()).append("\"");
          lexer.advance();
        }
        fail("Too many tokens. Following tokens: " + remainingTokens.toString());
      }
      String tokenName = lexer.getTokenType().toString();
      assertEquals("Token mismatch at position " + idx, expectedTokens[idx], tokenName);
      idx++;
      lexer.advance();
    }

    if (idx < expectedTokens.length) fail("Not enough tokens");
  }
}
