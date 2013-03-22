package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyTestCase;

/**
 * User: ktisha
 */
public class PyProtectedMemberInspectionTest extends PyTestCase {

  public void testTruePositive() {
    doTest();
  }

  public void testTruePositiveInClass() {
    doTest();
  }

  public void testTrueNegative() {
    doTest();
  }

  private void doTest() {
    myFixture.configureByFile("inspections/PyProtectedMemberInspection/" + getTestName(true) + ".py");
    myFixture.enableInspections(PyProtectedMemberInspection.class);
    myFixture.checkHighlighting(false, false, true);
  }
}
