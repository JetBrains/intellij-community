/*
 * User: anna
 * Date: 28-Oct-2009
 */
package com.intellij.codeInsight;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.openapi.application.PathManager;

public class ConvertToThreadLocalIntentionTest extends LightQuickFixTestCase {

  @Override
  protected boolean shouldBeAvailableAfterExecution() {
    return true;
  }

  @Override
  protected String getBasePath() {
    return "/intentions/threadLocal";
  }

  @Override
  protected String getTestDataPath() {
    return PathManager.getHomePath() + "/plugins/typeMigration/testData";
  }

  public void test() throws Exception {
    doAllTests();
  }
}