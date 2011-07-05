package com.jetbrains.python;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;

/**
 * @author yole
 */
public class PyFormatterTest extends PyLightFixtureTestCase {
  public void testBlankLineBetweenMethods() {
    doTest();
  }

  public void testBlankLineAroundClasses() {
    CodeStyleSettingsManager.getSettings(myFixture.getProject()).BLANK_LINES_AROUND_CLASS = 2;
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

  public void testSpecialSlice() {  // PY-1928
    doTest();
  }

  public void testNoWrapBeforeParen() {  // PY-3172
    doTest();
  }

  public void testTupleAssignment() {  // PY-4034 comment
    doTest();
  }

  public void testPsiFormatting() { // IDEA-69724
    String initial =
      "def method_name(\n" +
      "   desired_impulse_response,\n" +
      " desired_response_parameters,\n" +
      " inverse_filter_length, \n" +
      " observed_impulse_response):\n" +
      " #  Extract from here to ...\n" +
      "   desired_impulse_response = {'dirac, 'gaussian', logistic_derivative'}\n" +
      "return desired,                o";
    
    final PsiFile file = PyElementGenerator.getInstance(myFixture.getProject()).createDummyFile(LanguageLevel.PYTHON30, initial);
    final PsiElement reformatted = CodeStyleManager.getInstance(myFixture.getProject()).reformat(file);

    String expected =
      "def method_name(\n" +
      "desired_impulse_response,\n" +
      "desired_response_parameters,\n" +
      "inverse_filter_length,\n" +
      "observed_impulse_response):\n" +
      "#  Extract from here to ...\n" +
      "    desired_impulse_response = {'dirac, '\n" +
      "    gaussian\n" +
      "    ', logistic_derivative'}\n" +
      "    return desired, o";
    assertEquals(expected, reformatted.getText());
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
