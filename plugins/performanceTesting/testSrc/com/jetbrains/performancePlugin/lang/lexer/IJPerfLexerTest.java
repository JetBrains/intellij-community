package com.jetbrains.performancePlugin.lang.lexer;

import com.intellij.lexer.Lexer;
import com.intellij.testFramework.LexerTestCase;
import com.jetbrains.performancePlugin.TestUtil;
import com.jetbrains.performancePlugin.lang.IJPerfFileType;

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
  protected Lexer createLexer() {
    return new IJPerfLexerAdapter();
  }

  @Override
  protected String getDirPath() {
    return TestUtil.getDataSubPath("lexer");
  }
}