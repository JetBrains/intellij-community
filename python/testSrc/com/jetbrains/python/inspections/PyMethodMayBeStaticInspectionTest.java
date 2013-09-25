package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyTestCase;

/**
 * User: ktisha
 */
public class PyMethodMayBeStaticInspectionTest extends PyTestCase {

  public void testTruePositive() {
    doTest();
  }

  public void testTrueNegative() {
    doTest();
  }

  public void testEmpty() {
    doTest();
  }

  public void testInit() {
    doTest();
  }

  public void testWithQualifier() {
    doTest();
  }

  public void testStaticMethod() {
    doTest();
  }

  public void testClassMethod() {
    doTest();
  }

  public void testProperty() {
    doTest();
  }

  public void testSelfName() {
    doTest();
  }

  public void testNotImplemented() {
    doTest();
  }

  public void testOverwrittenMethod() {
    doTest();
  }

  public void testSuperMethod() {
    doTest();
  }

  public void testAbstractProperty() {
    doTest();
  }

  private void doTest() {
    myFixture.configureByFile("inspections/PyMethodMayBeStaticInspection/" + getTestName(true) + ".py");
    myFixture.enableInspections(PyMethodMayBeStaticInspection.class);
    myFixture.checkHighlighting(false, false, true);
  }
}
