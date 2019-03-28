package com.intellij.bash.lexer;

import com.intellij.lexer.Lexer;
import com.intellij.testFramework.LexerTestCase;
import org.jetbrains.annotations.NotNull;

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
    return getDirPath() + "/" + getTestName(true) + extension;
  }

  public void testFirst()       { doFileTest("sh"); }
  public void testHello()       { doFileTest("sh"); }
  public void testExprs()       { doFileTest("sh"); }
  public void testCase()        { doFileTest("sh"); }
  public void testFor()         { doFileTest("sh"); }
  public void testIf()          { doFileTest("sh"); }
  public void testHeredoc()     { doFileTest("sh"); }
  public void testTrap()        { doFileTest("sh"); }
  public void testTrap2()       { doFileTest("sh"); }
  public void testLet()         { doFileTest("sh"); }
  public void testParams()      { doFileTest("sh"); }
  public void testSelect()      { doFileTest("sh"); }
  public void testBinaryData()  { doFileTest("bash"); }
}
