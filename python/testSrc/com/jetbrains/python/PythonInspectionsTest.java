package com.jetbrains.python;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;
import com.jetbrains.python.inspections.*;

/**
 * @author yole
 */
public class PythonInspectionsTest extends PyLightFixtureTestCase {
  public void testReturnValueFromInit() throws Throwable {
    final JythonManager manager = JythonManager.getInstance();
    manager.execScriptFromResource("inspections/inspections.py"); // could be moved to setUp() if more jython-based inspections existed
    doTest(getTestName(true), PythonPyInspectionToolProvider.getInstance().createLocalInspectionTool("ReturnValueFromInitInspection"));
  }

  private void doTest(String testName, LocalInspectionTool localInspectionTool) throws Throwable {
    myFixture.testInspection(testName, new LocalInspectionToolWrapper(localInspectionTool));
  }

  public void testPyMethodFirstArgAssignmentInspection() throws Throwable {
    LocalInspectionTool inspection = new PyMethodFirstArgAssignmentInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyUnreachableCodeInspection() throws Throwable {
    LocalInspectionTool inspection = new PyUnreachableCodeInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyUnresolvedReferencesInspection() throws Throwable {
    LocalInspectionTool inspection = new PyUnresolvedReferencesInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyArgumentListInspection() throws Throwable {
    LocalInspectionTool inspection = new PyArgumentListInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyRedeclarationInspection() throws Throwable {
    LocalInspectionTool inspection = new PyRedeclarationInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyStringFormatInspection() throws Throwable {
    LocalInspectionTool inspection = new PyStringFormatInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyMethodOverridingInspection() throws Throwable {
    LocalInspectionTool inspection = new PyMethodOverridingInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyTrailingSemicolonInspection() throws Throwable {
    LocalInspectionTool inspection = new PyTrailingSemicolonInspection();
    doTest(getTestName(false), inspection);
  }

  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/inspections/";
  }
}
