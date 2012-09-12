package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;

/**
 * @author vlan
 */
public class PyUnreachableCodeInspectionTest extends PyTestCase {
  private static final String TEST_DIRECTORY = "inspections/PyUnreachableCodeInspection/";

  // All previous unreachable tests, feel free to split them
  public void testUnreachable() {
    runWithLanguageLevel(LanguageLevel.PYTHON26, new Runnable() {
      @Override
      public void run() {
        doTest();
      }
    });
  }

  // PY-7420
  public void testWithSuppressedExceptions() {
    runWithLanguageLevel(LanguageLevel.PYTHON26, new Runnable() {
      @Override
      public void run() {
        doTest();
      }
    });
  }

  private void doTest() {
    myFixture.configureByFile(TEST_DIRECTORY + getTestName(false) + ".py");
    myFixture.enableInspections(PyUnreachableCodeInspection.class);
    myFixture.checkHighlighting(true, false, false);
  }
}
