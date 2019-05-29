// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.rename;

import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShRenameTest extends LightPlatformCodeInsightTestCase {

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("sh") + "/testData/rename/";
  }

  public void testBasic1() {
    doTest("MY_NAME");
  }

  public void testBasic2() {
    doTest(null);
  }

  public void testBasic3() {
    doTest("command -v");
  }

  public void testSelection1() {
    doTest("Bye");
  }

  public void testSelection2() {
    doTest("zsh");
  }

  public void testSelection3() {
    doTest("m");
  }

  public void testSelection4() {
    doTest("4]]");
  }

  private void doTest(@Nullable String newName) {
    configureByFile(getTestName(true) + "-before.sh");
    TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
    executeAction(IdeActions.ACTION_RENAME);
    TemplateState templateState = TemplateManagerImpl.getTemplateState(getEditor());
    if (newName != null) {
      assertNotNull(templateState);
    }
    else {
      assertNull(templateState);
      return;
    }
    assertFalse(templateState.isFinished());
    type(newName);
    templateState.gotoEnd();
    checkResultByFile(getTestName(true) + "-after.sh");
  }
}
