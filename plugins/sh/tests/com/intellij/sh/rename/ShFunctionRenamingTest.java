// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.rename;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShFunctionRenamingTest extends BasePlatformTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("sh") + "/testData/rename/";
  }

  public void testFunctionCaseOne() {
    doTest("echo");
  }

  public void testFunctionCaseTwo() {
    doTest("foo");
  }

  public void testFunctionIDEA263122() {
    doTest("addVersion");
  }

  public void testOverridingFunction() {
    doTest("bar");
  }

  public void testFunctionMultipleCases() {
    doTest("exit");
  }

  private void doTest(@Nullable String newName) {
    myFixture.configureByFile(getTestName(true) + ".before.sh");
    myFixture.renameElementAtCaret(newName);
    myFixture.checkResultByFile(getTestName(true) + ".after.sh");
  }
}
