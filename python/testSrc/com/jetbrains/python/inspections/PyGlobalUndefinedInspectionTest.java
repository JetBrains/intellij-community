package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyTestCase;

/**
 * User: ktisha
 */
public class PyGlobalUndefinedInspectionTest extends PyTestCase {

  public void testTruePositive() {
    doTest();
  }

  public void testTrueNegative() {
    doTest();
  }

  private void doTest() {
    myFixture.configureByFile("inspections/PyGlobalUndefinedInspection/" + getTestName(true) + ".py");
    myFixture.enableInspections(PyGlobalUndefinedInspection.class);
    myFixture.checkHighlighting(false, false, true);
  }
}
