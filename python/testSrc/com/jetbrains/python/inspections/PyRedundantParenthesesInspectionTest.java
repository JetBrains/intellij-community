package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;

/**
 * @author yole
 */
public class PyRedundantParenthesesInspectionTest extends PyTestCase {
  public void doTest() {
    myFixture.configureByFile("inspections/PyRedundantParenthesesInspection/" + getTestName(false) + ".py");
    myFixture.enableInspections(PyRedundantParenthesesInspection.class);
    myFixture.checkHighlighting(true, false, true);
  }

  public void doTest(LanguageLevel languageLevel) {
    try {
      setLanguageLevel(languageLevel);
      myFixture.configureByFile("inspections/PyRedundantParenthesesInspection/" + getTestName(false) + ".py");
      myFixture.enableInspections(PyRedundantParenthesesInspection.class);
      myFixture.checkHighlighting(true, false, true);
    } finally {
      setLanguageLevel(null);
    }
  }

  public void testBooleanMultiline() {
    doTest();
  }

  public void testFormatting() {
    doTest();
  }

  public void testIfElif() {
    doTest();
  }

  public void testIfMultiline() {
    doTest();
  }

  public void testStringMultiline() {
    doTest();
  }

  public void testTryExcept() {
    doTest();
  }

  public void testTryExceptNegate() {
    doTest();
  }

  public void testWhile() {
    doTest();
  }

  public void testYieldFrom() {       //PY-7410
    doTest(LanguageLevel.PYTHON33);
  }

  public void testYield() {       //PY-10420
    doTest(LanguageLevel.PYTHON27);
  }

}
