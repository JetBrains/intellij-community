package com.jetbrains.python;

import com.intellij.lexer.Lexer;
import com.jetbrains.python.codeInsight.regexp.PythonRegexpParserDefinition;
import com.jetbrains.python.fixtures.PyLexerTestCase;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;

/**
 * @author yole
 */
public class PyRegexpTest extends PyLightFixtureTestCase {
  public void testNestedCharacterClasses() {  // PY-2908
    doTestHighlighting();
  }

  public void testNestedCharacterClassesLexer() {
    doTestLexer("[][]", "CLASS_BEGIN", "CHARACTER", "CHARACTER", "CLASS_END");
  }

  public void testNestedCharacterClasses2() {  // PY-2908
    doTestHighlighting();
  }

  public void testOctal() {  // PY-2906
    doTestHighlighting();
  }

  public void testBraceInPythonCharacterClass() {  // PY-1929
    doTestHighlighting();
  }

  public void testNegatedBraceInCharacterClass() {
    doTestLexer("[^][]", "CLASS_BEGIN", "CARET", "CHARACTER", "CHARACTER", "CLASS_END");
  }

  private void doTestHighlighting() {
    myFixture.testHighlighting(true, false, false, "regexp/" + getTestName(true) + ".py");
  }

  private void doTestLexer(final String text, String... expectedTokens) {
    Lexer lexer = new PythonRegexpParserDefinition().createLexer(myFixture.getProject());
    PyLexerTestCase.doLexerTest(text, lexer, expectedTokens);
  }
}
