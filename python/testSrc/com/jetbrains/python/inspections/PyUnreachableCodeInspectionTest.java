package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;

/**
 * @author vlan
 */
public class PyUnreachableCodeInspectionTest extends PyTestCase {
  private static final String TEST_DIRECTORY = "inspections/PyUnreachableCodeInspection/";

  // All previous unreachable tests
  public void testUnreachable() {
    doTest();
  }

  private void doTest() {
    runWithLanguageLevel(LanguageLevel.PYTHON27, new Runnable() {
      @Override
      public void run() {
        myFixture.configureByFile(TEST_DIRECTORY + getTestName(false) + ".py");
        myFixture.enableInspections(PyUnreachableCodeInspection.class);
        myFixture.checkHighlighting(true, false, false);
      }
    });
  }
}
