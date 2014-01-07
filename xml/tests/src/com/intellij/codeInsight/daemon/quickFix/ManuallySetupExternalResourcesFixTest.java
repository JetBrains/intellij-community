package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class ManuallySetupExternalResourcesFixTest extends LightQuickFixParameterizedTestCase {
  public void test() throws Exception { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/quickFix/manuallySetupExternalResources";
  }

  // just check for action availability
  @Override
  protected void doAction(String text, boolean actionShouldBeAvailable, String testFullPath, String testName) throws Exception {
    IntentionAction action = findActionWithText(text);
    if (action == null && actionShouldBeAvailable) {
      fail("Action with text '" + text + "' is not available in test " + testFullPath);
    }
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/xml/tests/testData/";
  }
}
