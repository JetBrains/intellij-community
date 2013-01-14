package com.jetbrains.python.inspections;

import com.intellij.testFramework.LightProjectDescriptor;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

/**
 * @author vlan
 */
public class Py3UnresolvedReferencesInspectionTest extends PyTestCase {
  private static final String TEST_DIRECTORY = "inspections/PyUnresolvedReferencesInspection3K/";

  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return ourPy3Descriptor;
  }

  private void doMultiFileTest(@NotNull final String filename, @NotNull LanguageLevel level) {
    runWithLanguageLevel(level, new Runnable() {
      @Override
      public void run() {
        final String testName = getTestName(false);
        myFixture.copyDirectoryToProject(TEST_DIRECTORY + testName, "");
        myFixture.configureFromTempProjectFile(filename);
        myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
        myFixture.checkHighlighting(true, false, false);
      }
    });
  }

  private void doMultiFileTest(@NotNull String filename) {
    doMultiFileTest(filename, LanguageLevel.PYTHON33);
  }

  public void testNamedTupleStub() {
    doMultiFileTest("a.py");
  }
}
