/*
 * User: anna
 * Date: 06-Mar-2008
 */
package com.jetbrains.python;

import com.intellij.codeInsight.completion.CodeCompletionHandler;
import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.LightCodeInsightTestCase;

public class PythonCompletionTest extends LightCodeInsightTestCase{

  private void doTest() throws Exception {
    final String testName = getTestName(false);
    configureByFile(testName + ".py");
    new CodeCompletionHandler().invoke(getProject(), getEditor(), getFile());
    checkResultByFile(testName + ".after.py");
  }

  protected String getTestDataPath() {
    return PathManager.getHomePath() + "/plugins/python/testData/completion/";
  }

  public void testLocalVar() throws Exception {
    doTest();
  }

  public void testSelfMethod() throws Exception {
    doTest();
  }
}