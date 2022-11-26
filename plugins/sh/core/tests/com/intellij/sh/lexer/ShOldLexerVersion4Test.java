// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.lexer;

import com.intellij.lexer.Lexer;
import com.intellij.testFramework.LexerTestCase;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.application.PluginPathManager;

public class ShOldLexerVersion4Test extends LexerTestCase {
  @Override
  protected Lexer createLexer() {
    return new ShLexer();
  }

  @Override
  protected String getDirPath() {
    return PluginPathManager.getPluginHomePath("sh") + "/core/testData/oldLexer/v4";
  }

  @NotNull
  @Override
  protected String getPathToTestDataFile(String extension) {
    return getDirPath() + "/" + getTestName(true) + extension;
  }

  public void testCasePattern() {
    doFileTest("sh");
  }

  public void testIssue469() {
    doFileTest("sh");
  }

  public void testV4Lexing() {
    doFileTest("sh");
  }

  public void testIssue243() {
    doFileTest("sh");
  }
}

