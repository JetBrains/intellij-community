package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyTestCase;

/**
 * User: ktisha
 */
public class PyNoneFunctionAssignmentInspectionTest extends PyTestCase {

  public void testPass() {
    doTest();
  }

  public void testReturnNone() {
    doTest();
  }

  public void testNoReturn() {
    doTest();
  }

  public void testTrueNegative() {
    doTest();
  }

  public void testNoType() {
    doTest();
  }

  // PY-10883
  public void testMethodWithInheritors() {
    doTest();
  }

  // PY-10883
  public void testDecoratedMethod() {
    doTest();
  }

  private void doTest() {
    myFixture.configureByFile("inspections/PyNoneFunctionAssignmentInspection/" + getTestName(true) + ".py");
    myFixture.enableInspections(PyNoneFunctionAssignmentInspection.class);
    myFixture.checkHighlighting(false, false, true);
  }
}
