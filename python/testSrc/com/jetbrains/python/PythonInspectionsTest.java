package com.jetbrains.python;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.testFramework.InspectionTestCase;
import com.jetbrains.python.inspections.*;

/**
 * @author yole
 */
public class PythonInspectionsTest extends InspectionTestCase {
  @Override
  protected Sdk getTestProjectJdk() {
    return PythonMockSdk.findOrCreate();
  }

  public void testReturnValueFromInit() throws Exception {
    final JythonManager manager = JythonManager.getInstance();
    manager.execScriptFromResource("inspections/inspections.py"); // could be moved to setUp() if more jython-based inspections existed
    doTest(getTestName(true), PythonPyInspectionToolProvider.getInstance().createLocalInspectionTool("ReturnValueFromInitInspection"));
  }

  public void testPyMethodFirstArgAssignmentInspection() throws Exception {
    LocalInspectionTool inspection = new PyMethodFirstArgAssignmentInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyUnreachableCodeInspection() throws Exception {
    LocalInspectionTool inspection = new PyUnreachableCodeInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyUnresolvedReferencesInspection() throws Exception {
    LocalInspectionTool inspection = new PyUnresolvedReferencesInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyArgumentListInspection() throws Exception {
    LocalInspectionTool inspection = new PyArgumentListInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyRedeclarationInspection() throws Exception {
    LocalInspectionTool inspection = new PyRedeclarationInspection();
    doTest(getTestName(false), inspection);
  }

  protected String getTestDataPath() {
    return PathManager.getHomePath() + "/plugins/python/testData/inspections/";
  }
}
