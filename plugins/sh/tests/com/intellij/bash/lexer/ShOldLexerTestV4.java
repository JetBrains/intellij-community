package com.intellij.bash.lexer;

import com.intellij.lexer.Lexer;
import com.intellij.testFramework.LexerTestCase;
import org.jetbrains.annotations.NotNull;

public class ShOldLexerTestV4 extends LexerTestCase {
  @Override
  protected Lexer createLexer() {
    return new ShLexer();
  }

  @Override
  protected String getDirPath() {
    return "testData/oldLexer/v4";
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

