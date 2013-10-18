package com.jetbrains.python.inspections;

import com.intellij.testFramework.LightProjectDescriptor;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;

/**
 * @author vlan
 */
public class Py3TypeCheckerInspectionTest extends PyTestCase {
  public static final String TEST_DIRECTORY = "inspections/PyTypeCheckerInspection/";

  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return ourPy3Descriptor;
  }

  private void doTest() {
    runWithLanguageLevel(LanguageLevel.PYTHON32, new Runnable() {
      @Override
      public void run() {
        myFixture.configureByFile(TEST_DIRECTORY + getTestName(false) + ".py");
        myFixture.enableInspections(PyTypeCheckerInspection.class);
        myFixture.checkHighlighting(true, false, true);
      }
    });
  }

  private void doMultiFileTest() {
    runWithLanguageLevel(LanguageLevel.PYTHON32, new Runnable() {
      @Override
      public void run() {
        myFixture.copyDirectoryToProject(TEST_DIRECTORY + getTestName(false), "");
        myFixture.configureFromTempProjectFile("a.py");
        myFixture.enableInspections(PyTypeCheckerInspection.class);
        myFixture.checkHighlighting(true, false, true);
      }
    });
  }

  // PY-9289
  public void testWithOpenBinaryPy3() {
    doTest();
  }

  // PY-10660
  public void testStructUnpackPy3() {
    doMultiFileTest();
  }

  public void testBuiltinsPy3() {
    doTest();
  }
}
