// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.rename;

import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.sh.backend.rename.ShRenameAllOccurrencesHandler;
import com.intellij.sh.highlighting.ShHighlightUsagesTest;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShRenameAllOccurrencesTest extends LightPlatformCodeInsightTestCase {

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("sh") + "/core/testData/rename/";
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

  public void testNotFunction() {
    doTest("bash");
  }

  public void testKeyword1() {
    doTest(null);
  }

  public void testKeyword2() {
    doTest(null);
  }

  public void testSuppressedRename() {
    ShHighlightUsagesTest.suppressOccurrences(getTestRootDisposable());
    doTest(null);
  }

  private void doTest(@Nullable String newName) {
    configureByFile(getTestName(true) + "-before.sh");
    TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
    executeRenameAction(newName != null);
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
    doTestWithPlainTextRenamer(newName);
  }

  private void executeRenameAction(boolean expectEnabled) {
    try {
      executeAction(IdeActions.ACTION_RENAME);
    }
    catch (AssertionError e) {
      if (expectEnabled) {
        throw e;
      }
    }
  }

  private void doTestWithPlainTextRenamer(@NotNull String newName) {
    configureByFile(getTestName(true) + "-before.sh");
    runWithoutInplaceRename(() -> {
      executeAction(IdeActions.ACTION_RENAME);
      getEditor().getUserData(ShRenameAllOccurrencesHandler.RENAMER_KEY).renameTo(newName);
    });
    checkResultByFile(getTestName(true) + "-after.sh");
  }

  static void runWithoutInplaceRename(@NotNull Runnable runnable) {
    Disposable parentDisposable = Disposer.newDisposable();
    ShRenameAllOccurrencesHandler.getMaxInplaceRenameSegmentsRegistryValue().setValue(-1, parentDisposable);
    try {
      runnable.run();
    }
    finally {
      Disposer.dispose(parentDisposable);
    }
  }
}
