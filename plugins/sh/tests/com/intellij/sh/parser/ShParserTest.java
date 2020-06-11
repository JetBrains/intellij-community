// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.parser;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.ParsingTestCase;

public class ShParserTest extends ParsingTestCase {
  public ShParserTest() {
    super("parser", "sh", true, new ShParserDefinition());
  }

  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("sh") + "/testData";
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
  public void testRegex2()        { doTest(true); }
  public void testRedirection()   { doTest(true); }
  public void testRedirection2()  { doTest(true); }
  public void testSource()        { doTest(true); }
  public void testRedirectErrors(){ doTest(true); }
}
