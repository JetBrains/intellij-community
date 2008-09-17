/*
 * User: anna
 * Date: 06-Mar-2008
 */
package com.jetbrains.python;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.LightCodeInsightTestCase;

public class PythonCompletionTest extends LightCodeInsightTestCase{

  private void doTest() throws Exception {
    final String testName = getTestName(true);
    configureByFile(testName + ".py");
    new CodeCompletionHandlerBase(CompletionType.BASIC).invoke(getProject(), getEditor(), getFile());
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

  public void testSelfField() throws Exception {
    doTest();
  }
}