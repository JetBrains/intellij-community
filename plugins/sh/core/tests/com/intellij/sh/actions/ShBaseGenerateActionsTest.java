// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.actions;

import com.intellij.codeInsight.actions.CodeInsightAction;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.sh.backend.actions.ShGenerateForLoop;
import com.intellij.sh.backend.actions.ShGenerateUntilLoop;
import com.intellij.sh.backend.actions.ShGenerateWhileLoop;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;

public class ShBaseGenerateActionsTest extends BasePlatformTestCase {
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("sh") + "/core/testData/generate/";
  }

  public void testForLoop() {
    doTest(new ShGenerateForLoop());
  }

  public void testForLoopInsert() {
    doTest(new ShGenerateForLoop());
  }

  public void testWhileLoop() {
    doTest(new ShGenerateWhileLoop());
  }

  public void testWhileLoopInsert() {
    doTest(new ShGenerateWhileLoop());
  }

  public void testUntilLoop() {
    doTest(new ShGenerateUntilLoop());
  }

  public void testUntilLoopInsert() {
    doTest(new ShGenerateUntilLoop());
  }

  private void doTest(@NotNull CodeInsightAction action) {
    String testName = getTestName(true);
    myFixture.configureByFile(testName + ".sh");
    Project project = myFixture.getProject();
    Editor editor = myFixture.getEditor();
    action.actionPerformedImpl(project, editor);
    myFixture.checkResultByFile(testName + ".after.sh");
  }
}