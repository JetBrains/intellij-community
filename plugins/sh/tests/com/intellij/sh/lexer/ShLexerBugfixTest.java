// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.lexer;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.LexerTestCase;
import org.jetbrains.annotations.NotNull;

public class ShLexerBugfixTest extends LexerTestCase {
  @Override
  protected Lexer createLexer() {
    return new ShLexer();
  }

  @Override
  protected String getDirPath() {
    return PluginPathManager.getPluginHomePath("sh") + "/testData/lexer/bugfix";
  }

  @NotNull
  @Override
  protected String getPathToTestDataFile(String extension) {
    return getDirPath() + "/" + getTestName(true) + extension;
  }

  /**
   * IDEA-219928
   */
  public void testParamExpansionEscape() { doFileTest("sh"); }

  /**
   * IDEA-220072
   */
  public void testProcessSubstitution()  { doFileTest("sh"); }
}