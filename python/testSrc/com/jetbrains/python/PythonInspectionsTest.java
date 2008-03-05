package com.jetbrains.python;

import com.intellij.testFramework.InspectionTestCase;
import com.intellij.openapi.application.PathManager;

/**
 * @author yole
 */
public class PythonInspectionsTest extends InspectionTestCase {
  public void testReturnValueFromInit() throws Exception {
    doTest(getTestName(true), PyInspectionToolProvider.getInstance().createLocalInspectionTool("ReturnValueFromInitInspection"));
  }

  protected String getTestDataPath() {
    return PathManager.getHomePath() + "/plugins/python/testData/inspections/";
  }
}
