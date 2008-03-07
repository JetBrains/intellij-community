/*
 * User: anna
 * Date: 06-Mar-2008
 */
package com.jetbrains.python;

import com.intellij.codeInsight.daemon.LightIntentionActionTestCase;
import com.intellij.openapi.application.PathManager;

public class PythonDemorganLawIntentionTest extends LightIntentionActionTestCase {
  public void test() throws Exception { doAllTests(); }

  protected String getBasePath() {
    return "/demorgan";
  }

  protected String getTestDataPath() {
    return PathManager.getHomePath() + "/plugins/python/testData/intentions";
  }

  @Override
  protected boolean shouldBeAvailableAfterExecution() {
    return true;
  }
}