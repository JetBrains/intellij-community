// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.editor;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;

public class ShFoldingTest extends BasePlatformTestCase {

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("sh") + "/testData/folding/";
  }

  public void testFunction() { doTest(); }
  public void testHeredoc()  { doTest(); }
  public void testDoBlock()  { doTest(); }
  public void testComment()  { doTest(); }

  private void doTest() {
    myFixture.testFoldingWithCollapseStatus(getTestDataPath() + "/" + getTestName(true) + ".sh");
  }
}