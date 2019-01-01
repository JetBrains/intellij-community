package com.intellij.bash.parser;

import com.intellij.testFramework.ParsingTestCase;

public class BashParserTest extends ParsingTestCase {
  public BashParserTest() {
    super("parser", "sh", true, new BashParserDefinition());
  }

  @Override
  protected String getTestDataPath() {
    return "testData";
  }

  public void testShebang() { doTest(true); }
  public void testFirst()   { doTest(true); }
  public void testLines()   { doTest(true); }
  public void testCase()    { doTest(true); }
  public void testFor()     { doTest(true); }
}
