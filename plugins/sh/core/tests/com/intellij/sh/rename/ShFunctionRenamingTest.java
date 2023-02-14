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
    return PluginPathManager.getPluginHomePath("sh") + "/core/testData/rename/";
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

  public void testImportedFunctionRename() {
    myFixture.addFileToProject("source.sh", """
      #!/bin/zsh
      source ./target.sh
      foo""");
    myFixture.configureByText("target.sh", """
      #!/bin/zsh
      function <caret>foo() {
          echo "Simple text"
      }""");
    myFixture.renameElementAtCaret("bar");
    myFixture.checkResult("source.sh", """
      #!/bin/zsh
      source ./target.sh
      bar""", false);
    myFixture.checkResult("""
                            #!/bin/zsh
                            function <caret>bar() {
                                echo "Simple text"
                            }""");
  }

  private void doTest(@Nullable String newName) {
    myFixture.configureByFile(getTestName(true) + ".before.sh");
    myFixture.renameElementAtCaret(newName);
    myFixture.checkResultByFile(getTestName(true) + ".after.sh");
  }
}
