package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyTestCase;

/**
 * User: ktisha
 */
public class PyAugmentAssignmentInspectionTest extends PyTestCase {

  public void testMult() {
    doTest();
  }

  public void testAdd() {
    doTest();
  }

  public void testNegativeAssignment() {
    doTest();
  }

  public void testNegativeName() {
    doTest();
  }

  public void testNegative() {
    doTest();
  }

  public void testNegativeMinus() {
    doTest();
  }

  public void testNegativeString() {
    doTest();
  }

  public void testString() {
    doTest();
  }

  public void testNumeric() {
    doTest();
  }

  public void testList() {
    doTest();
  }

  public void testDifferentOperations() {
    doTest();
  }

  // PY-7605
  public void testStrOrUnknownFirstArg() {
    doTest();
  }

  private void doTest() {
    myFixture.configureByFile("inspections/PyAugmentAssignmentInspection/" + getTestName(true) + ".py");
    myFixture.enableInspections(PyAugmentAssignmentInspection.class);
    myFixture.checkHighlighting(true, false, true);
  }
}
