package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyTestCase;

/**
 * User: ktisha
 */
public class PyPep8NamingInspectionTest extends PyTestCase {

  public void testFunctionVariable() {
    doTest();
  }

  public void testClassName() {
    doTest();
  }

  public void testArgumentName() {
    doTest();
  }

  public void testFunctionName() {
    doTest();
  }

  public void testImportConstant() {
    doTest();
  }

  public void testImportCamelAsLower() {
    doTest();
  }

  public void testImportLowerAsNonLower() {
    doTest();
  }

  public void testOverridden() {
    doTest();
  }

  public void testTest() {
    doTest();
  }

  public void testOverrideFromModule() {
    myFixture.configureByFiles("inspections/PyPep8NamingInspection/" + getTestName(true) + ".py",
                               "inspections/PyPep8NamingInspection/tmp1.py");
    myFixture.enableInspections(PyPep8NamingInspection.class);
    myFixture.checkHighlighting(false, false, true);
  }

  private void doTest() {
    myFixture.configureByFile("inspections/PyPep8NamingInspection/" + getTestName(true) + ".py");
    myFixture.enableInspections(PyPep8NamingInspection.class);
    myFixture.checkHighlighting(false, false, true);
  }
}
