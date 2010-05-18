package com.jetbrains.python;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;

/**
 * @author yole
 */
public class PyFormatterTest extends PyLightFixtureTestCase {
  public void testBlankLineBetweenMethods() throws Exception {
    doTest();
  }

  public void testBlankLineAroundClasses() throws Exception {
    doTest();
  }

  public void testSpaceAfterComma() throws Exception {
    doTest();
  }

  public void testPep8ExtraneousWhitespace() throws Exception {
    doTest();
  }

  public void testPep8Operators() throws Exception {
    doTest();
  }

  public void testPep8KeywordArguments() throws Exception {
    doTest();
  }

  public void testUnaryMinus() throws Exception {
    doTest();
  }

  public void testBlankLineAfterImports() throws Exception {
    doTest();
  }

  public void testBlankLineBeforeFunction() throws Exception {
    doTest();
  }

  private void doTest() throws Exception {
    myFixture.configureByFile("formatter/" + getTestName(true) + ".py");
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        CodeStyleManager.getInstance(myFixture.getProject()).reformat(myFixture.getFile());
      }
    });
    myFixture.checkResultByFile("formatter/" + getTestName(true) + "_after.py");
  }
}
