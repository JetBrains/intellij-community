package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyTestCase;

/**
 * User: ktisha
 */
public class PyClassHasNoInitInspectionTest extends PyTestCase {

  public void testClass() {
    doTest();
  }

  public void testTrueNegative() {
    doTest();
  }

  public void testParentClass() {
    doTest();
  }

  private void doTest() {
    myFixture.configureByFile("inspections/PyClassHasNoInitInspection/" + getTestName(true) + ".py");
    myFixture.enableInspections(PyClassHasNoInitInspection.class);
    myFixture.checkHighlighting(false, false, true);
  }
}
