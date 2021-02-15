package com.intellij.lang.properties;

import com.intellij.codeInsight.daemon.quickFix.ActionHint;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class AddNewlineAbovePlaceCaretTest extends LightQuickFixParameterizedTestCase {

  @Override
  protected @NonNls String getBasePath() {
    return "/properties/addnewline";
  }

  @Override
  protected void doAction(@NotNull ActionHint actionHint, @NotNull String testFullPath, @NotNull String testName) {
    executeAction("EditorStartNewLineBefore");

    checkResult(testName);
  }

  private void checkResult(@NotNull final String testName) {
    final String expectedFilePath = getBasePath() + "/after" + testName;
    checkResultByFile("In file: " + expectedFilePath, expectedFilePath, false);
  }
}
