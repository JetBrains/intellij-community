package com.jetbrains.python;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;

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

  public void testListAssignment() {  // PY-1522
    doTest();
  }

  public void testStarExpression() {  // PY-1523
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), LanguageLevel.PYTHON30);
    try {
      doTest();
    }
    finally {
      PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), null);
    }
  }

  public void testWrapTuple() {  // PY-1792
    doTest();
  }

  public void testSpaceAfterCommaWrappedLine() {  // PY-1065
    doTest();
  }

  public void testAlignInBinaryExpression() {
    doTest();
  }

  public void testAlignInStringLiteral() {
    doTest();
  }

  public void testComment() {  // PY-2108
    doTest();
  }

  public void testCommentBetweenClasses() { // PY-1598
    doTest();
  }

  public void testTwoLinesBetweenTopLevelClasses() { // PY-2765
    doTest();
  }

  public void testTwoLinesBetweenTopLevelFunctions() { // PY-2765
    doTest();
  }

  private void doTest() {
    myFixture.configureByFile("formatter/" + getTestName(true) + ".py");
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        CodeStyleManager.getInstance(myFixture.getProject()).reformat(myFixture.getFile());
      }
    });
    myFixture.checkResultByFile("formatter/" + getTestName(true) + "_after.py");
  }
}
