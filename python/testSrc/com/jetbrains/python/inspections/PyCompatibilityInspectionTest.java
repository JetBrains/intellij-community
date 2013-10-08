package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

/**
 * User : catherine
 */
public class PyCompatibilityInspectionTest extends PyTestCase {

  public void testDictCompExpression() {
    setLanguageLevel(LanguageLevel.PYTHON27);
    doTest();
  }

  public void testSetLiteralExpression() {
    setLanguageLevel(LanguageLevel.PYTHON27);
    doTest();
  }

  public void testSetCompExpression() {
    setLanguageLevel(LanguageLevel.PYTHON27);
    doTest();
  }

  public void testExceptBlock() {
    setLanguageLevel(LanguageLevel.PYTHON27);
    doTest();
  }

  public void testImportStatement() {
    setLanguageLevel(LanguageLevel.PYTHON27);
    doTest();
  }

  public void testImportErrorCaught() {
    setLanguageLevel(LanguageLevel.PYTHON27);
    doTest();
  }

  public void testStarExpression() {
    setLanguageLevel(LanguageLevel.PYTHON30);
    doTest();
  }

  public void testBinaryExpression() {
    setLanguageLevel(LanguageLevel.PYTHON27);
    doTest();
  }

  public void testNumericLiteralExpression() {
    doTest();
  }

  public void testStringLiteralExpression() {
    doTest();
  }

  public void testListCompExpression() {
    doTest();
  }

  public void testRaiseStatement() {
    doTest();
  }

  public void testRaiseFrom() {
    setLanguageLevel(LanguageLevel.PYTHON32);
    doTest();
  }

  public void testReprExpression() {
    doTest();
  }

  public void testWithStatement() {
    setLanguageLevel(LanguageLevel.PYTHON27);
    doTest();
  }

  public void testPyClass() {
    doTest();
  }

  public void testPrintStatement() {
    doTest();
  }

  public void testFromImportStatement() {
    doTest();
  }

  public void testAssignmentStatement() {
    doTest();
  }

  public void testTryExceptStatement() {
    doTest();
  }

  public void testImportElement() {
    doTest();
  }

  public void testCallExpression() {
    setLanguageLevel(LanguageLevel.PYTHON30);
    doTest();
  }

  public void testBasestring() {
    doTest();
  }

  public void testConditionalExpression() {
    doTest();
  }

  public void testClassBaseList() {
    doTest();
  }

  // PY-7763
  public void testEllipsisAsStatementPy2() {
    doTest(LanguageLevel.PYTHON33);
  }

  // PY-8606
  public void testEllipsisInSubscriptionPy2() {
    doTest(LanguageLevel.PYTHON33);
  }

  // PY-11047
  public void testRelativeImport() {
    doTest();
  }

  private void doTest(@NotNull LanguageLevel level) {
    runWithLanguageLevel(level, new Runnable() {
      @Override
      public void run() {
        doTest();
      }
    });
  }

  private void doTest() {
    myFixture.configureByFile("inspections/PyCompatibilityInspection/" + getTestName(true) + ".py");
    myFixture.enableInspections(PyCompatibilityInspection.class);
    myFixture.checkHighlighting(true, false, false);
  }
}
