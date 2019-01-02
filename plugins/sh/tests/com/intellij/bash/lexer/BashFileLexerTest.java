package com.intellij.bash.lexer;

import com.intellij.lexer.Lexer;
import com.intellij.testFramework.LexerTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class BashFileLexerTest extends LexerTestCase {
  @Override
  protected Lexer createLexer() {
    return new BashLexer();
  }

  @Override
  protected String getDirPath() {
    return "testData/lexer";
  }

  @NotNull
  @Override
  protected String getPathToTestDataFile(String extension) {
    return new File(getDirPath(), getTestName(true) + extension).getAbsolutePath();
  }

  public void testFirst()       { doFileTest("sh"); }
  public void testHello()       { doFileTest("sh"); }
  public void testCase()        { doFileTest("sh"); }
  public void testFor()         { doFileTest("sh"); }
  public void testIf()          { doFileTest("sh"); }
  public void testHeredoc()     { doFileTest("sh"); }
  public void testBinaryData()  { doFileTest("bash"); }
}
