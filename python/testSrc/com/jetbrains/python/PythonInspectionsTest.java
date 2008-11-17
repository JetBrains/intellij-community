package com.jetbrains.python;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.InspectionTestCase;
import com.jetbrains.python.inspections.PyUnresolvedReferencesInspection;
import com.jetbrains.python.inspections.PyArgumentListInspection;
import com.jetbrains.python.inspections.PyRedeclarationInspection;

/**
 * @author yole
 */
public class PythonInspectionsTest extends InspectionTestCase {
  public void testReturnValueFromInit() throws Exception {
    doTest(getTestName(true), PyInspectionToolProvider.getInstance().createLocalInspectionTool("ReturnValueFromInitInspection"));
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
