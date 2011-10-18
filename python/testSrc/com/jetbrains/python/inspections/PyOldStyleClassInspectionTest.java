package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;

/**
 * User : catherine
 */
public class PyOldStyleClassInspectionTest extends PyTestCase {

  public void testSlot() {
    doTest(getTestName(false));
  }

  public void testGetattr() {
    doTest(getTestName(false));
  }

  public void testSuper() {
    doTest(getTestName(false));
  }

  public void testSuper30() {
    setLanguageLevel(LanguageLevel.PYTHON30);
    doTest(getTestName(false));
  }

  private void doTest(String name) {
    myFixture.configureByFile("inspections/PyOldStyleClassesInspection/" + name + ".py");
    myFixture.enableInspections(PyOldStyleClassesInspection.class);
    myFixture.checkHighlighting(true, false, false);
  }
}
