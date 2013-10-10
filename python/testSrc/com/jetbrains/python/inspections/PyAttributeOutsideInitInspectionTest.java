package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyTestCase;

/**
 * User: ktisha
 */
public class PyAttributeOutsideInitInspectionTest extends PyTestCase {

  public void testTruePositive() {
    doTest();
  }

  public void testTrueNegative() {
    doTest();
  }

  public void testDefinedInSuperClass() {
    doTest();
  }

  public void testTestClass() {
    doTest();
  }

  public void testUnitTest() {
    doTest();
  }

  public void testFromSuperClassWithoutInit() {
    doTest();
  }

  public void testFromSuperHierarchy() {
    doTest();
  }

  public void testClassAttrs() {
    doTest();
  }

  public void testBaseClassAttrs() {
    doTest();
  }

  private void doTest() {
    myFixture.configureByFile("inspections/PyAttributeOutsideInitInspection/" + getTestName(true) + ".py");
    myFixture.enableInspections(PyAttributeOutsideInitInspection.class);
    myFixture.checkHighlighting(false, false, true);
  }
}
