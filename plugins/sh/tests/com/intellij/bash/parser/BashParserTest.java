package com.intellij.bash.parser;

import com.intellij.lang.*;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.ParsingTestCase;

import java.io.File;
import java.io.IOException;

public class BashParserTest extends ParsingTestCase {
  public BashParserTest() {
    super("parser", "sh", true, new BashParserDefinition());
  }

  @Override
  protected String getTestDataPath() {
    return "testData";
  }

  public void testTrickyStrings() { doTest(true); }
  public void testStrings()       { doTest(true); }
  public void testShebang()       { doTest(true); }
  public void testFirst()         { doTest(true); }
  public void testLines()         { doTest(true); }
  public void testCase()          { doTest(true); }
  public void testEcho()          { doTest(true); }
  public void testFor()           { doTest(true); }
  public void testSelect()        { doTest(true); }
  public void testExpr()          { doTest(true); }
  public void testHeredoc()       { doTest(true); }
  public void testHereEof()       { doTest(true); }
  public void testIf()            { doTest(true); }
  public void testConditional()   { doTest(true); }
  public void testBacktick()      { doTest(true); }
  public void testFunction()      { doTest(true); }
  public void testTrap()          { doTest(true); }
  public void testMacports()      { doTest(true); }
  public void testParams()        { doTest(true); }
  public void testDict()          { doTest(true); }
  public void testRecover()       { doTest(true); }
  public void testGit()           { doTest(true); }
  public void testGaudi()         { doTest(true); }
  public void testPipeline()      { doTest(true); }
  public void testCommands()      { doTest(true); }
  public void testRegex()         { doTest(true); }
  public void testRedirection()   { doTest(true); }
  public void testSource()        { doTest(true); }
}
