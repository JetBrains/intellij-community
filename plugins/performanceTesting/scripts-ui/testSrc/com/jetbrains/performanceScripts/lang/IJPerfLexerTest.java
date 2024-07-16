package com.jetbrains.performanceScripts.lang;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.LexerTestCase;
import com.jetbrains.performanceScripts.lang.lexer.IJPerfLexerAdapter;
import org.jetbrains.annotations.NotNull;

public class IJPerfLexerTest extends LexerTestCase {

  public void testDoublePrefixCommand() {
    doTest();
  }

  public void testSinglePrefixCommand() {
    doTest();
  }

  public void testSpaceSeparatedParameters() {
    doTest();
  }

  public void testSpaceSeparatedParametersValue() {
    doTest();
  }

  public void testCommandWithoutParams() {
    doTest();
  }

  public void testOptionWithValue() {
    doTest();
  }

  public void testNumberOptions() {
    doTest();
  }

  public void testPipeSeparatedOption() {
    doTest();
  }

  public void testComment() {
    doTest();
  }

  public void testFilePathInParameters() {
    doTest();
  }

  public void testScriptWithEmptyLines() {
    doTest();
  }

  public void testTextOptionWithSymbols() {
    doTest();
  }

  private void doTest() {
    doFileTest(IJPerfFileType.DEFAULT_EXTENSION);
  }

  @Override
  protected @NotNull Lexer createLexer() {
    return new IJPerfLexerAdapter();
  }

  @Override
  protected @NotNull String getPathToTestDataFile(@NotNull String extension) {
    return PathManager.getCommunityHomePath() + "/" + getDirPath() + "/" + getTestName(true) + extension;
  }

  @Override
  protected @NotNull String getDirPath() {
    return TestUtil.getDataSubPath("lexer");
  }
}