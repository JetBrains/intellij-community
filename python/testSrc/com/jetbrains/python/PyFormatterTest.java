package com.jetbrains.python;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;

/**
 * @author yole
 */
public class PyFormatterTest extends PyLightFixtureTestCase {
  public void testBlankLineBetweenMethods() {
    doTest();
  }

  public void testBlankLineAroundClasses() {
    doTest();
  }

  public void testSpaceAfterComma() {
    doTest();
  }

  public void testPep8ExtraneousWhitespace() {
    doTest();
  }

  public void testPep8Operators() {
    doTest();
  }

  public void testPep8KeywordArguments() {
    doTest();
  }

  public void testUnaryMinus() {
    doTest();
  }

  public void testBlankLineAfterImports() {
    doTest();
  }

  public void testBlankLineBeforeFunction() {
    doTest();
  }
  
  public void testStarArgument() {  // PY-1395
    doTest();
  }

  public void testDictLiteral() {  // PY-1461
    doTest();    
  }

  private void doTest() {
    myFixture.configureByFile("formatter/" + getTestName(true) + ".py");
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        CodeStyleManager.getInstance(myFixture.getProject()).reformat(myFixture.getFile());
      }
    });
    myFixture.checkResultByFile("formatter/" + getTestName(true) + "_after.py");
  }
}
