package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;

/**
 * @author yole
 */
public class PyPropertyAccessInspectionTest extends PyTestCase {
  public void testTest() {
    doTest();
  }

  public void testOverrideAssignment() {  // PY-2313
    doTest();
  }

  private void doTest() {
    setLanguageLevel(LanguageLevel.PYTHON26);
    myFixture.configureByFile("inspections/PyPropertyAccessInspection/" + getTestName(true) + ".py");
    myFixture.enableInspections(PyPropertyAccessInspection.class);
    myFixture.checkHighlighting(true, false, false);
  }
}
