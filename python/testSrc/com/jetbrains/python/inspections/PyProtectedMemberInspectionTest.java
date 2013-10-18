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

  public void testDoubleUnderscore() {
    doTest();
  }

  public void testOuterClass() {
    doTest();
  }

  public void testSelfField() {
    doTest();
  }

  public void testModule() {
    myFixture.configureByFiles(getTestName(true) + ".py", "tmp.py");
    myFixture.enableInspections(PyProtectedMemberInspection.class);
    myFixture.checkHighlighting(false, false, true);
  }

  private void doTest() {
    myFixture.configureByFile(getTestName(true) + ".py");
    myFixture.enableInspections(PyProtectedMemberInspection.class);
    myFixture.checkHighlighting(false, false, true);
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/inspections/PyProtectedMemberInspection/";
  }
}
