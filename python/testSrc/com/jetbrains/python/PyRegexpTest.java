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
    doTest();
  }

  public void testNestedCharacterClassesLexer() {
    Lexer lexer = new PythonRegexpParserDefinition().createLexer(myFixture.getProject());
    PyLexerTestCase.doLexerTest("[][]", lexer, "CLASS_BEGIN", "CHARACTER", "CHARACTER", "CLASS_END");
  }

  public void testNestedCharacterClasses2() {  // PY-2908
    doTest();
  }

  public void testOctal() {  // PY-2906
    doTest();
  }

  public void testBraceInPythonCharacterClass() {  // PY-1929
    doTest();
  }

  private void doTest() {
    myFixture.testHighlighting(true, false, false, "regexp/" + getTestName(true) + ".py");
  }
}
