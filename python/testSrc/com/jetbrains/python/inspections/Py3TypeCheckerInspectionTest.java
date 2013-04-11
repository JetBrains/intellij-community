package com.jetbrains.python.inspections;

import com.intellij.testFramework.LightProjectDescriptor;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;

/**
 * @author vlan
 */
public class Py3TypeCheckerInspectionTest extends PyTestCase {
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return ourPy3Descriptor;
  }

  private void doTest() {
    runWithLanguageLevel(LanguageLevel.PYTHON32, new Runnable() {
      @Override
      public void run() {
        myFixture.configureByFile("inspections/PyTypeCheckerInspection/" + getTestName(false) + ".py");
        myFixture.enableInspections(PyTypeCheckerInspection.class);
        myFixture.checkHighlighting(true, false, true);
      }
    });
  }

  // PY-9289
  public void testWithOpenBinaryPy3() {
    doTest();
  }
}
