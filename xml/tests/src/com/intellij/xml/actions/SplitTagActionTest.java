package com.intellij.xml.actions;

import com.intellij.codeInsight.daemon.LightIntentionActionTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
@SuppressWarnings({"ALL"})
public class SplitTagActionTest extends LightIntentionActionTestCase {
  public void test() throws Exception { doAllTests(); }

  @Override
  protected boolean shouldBeAvailableAfterExecution() {
    return true;
  }

  @Override
  protected String getBasePath() {
    return "";
  }

  @NotNull
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/xml/tests/testData/intentions/splitTag";
  }
}