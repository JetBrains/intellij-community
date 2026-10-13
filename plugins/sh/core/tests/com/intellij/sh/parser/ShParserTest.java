// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.parser;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.project.Project;
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.ParsingTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.ParsingTestFixtureKt.parsingFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;

@TestApplication
public class ShParserTest {
  private static final TestFixture<Project> projectFixture = projectFixture();

  private final TestFixture<ParsingTestFixture> parsingFixture =
    parsingFixture(projectFixture, PluginPathManager.getPluginHomePath("sh") + "/core/testData/parser", "sh", true, new ShParserDefinition());

  @Test public void testArithmeticExpansion() { parsingFixture.get().doTest(true); }
  @Test public void testTrickyStrings() { parsingFixture.get().doTest(true); }
  @Test public void testStrings()       { parsingFixture.get().doTest(true); }
  @Test public void testStrings2()      { parsingFixture.get().doTest(true); }
  @Test public void testShebang()       { parsingFixture.get().doTest(true); }
  @Test public void testFirst()         { parsingFixture.get().doTest(true); }
  @Test public void testLines()         { parsingFixture.get().doTest(true); }
  @Test public void testCase()          { parsingFixture.get().doTest(true); }
  @Test public void testEcho()          { parsingFixture.get().doTest(true); }
  @Test public void testFor()           { parsingFixture.get().doTest(true); }
  @Test public void testSelect()        { parsingFixture.get().doTest(true); }
  @Test public void testExpr()          { parsingFixture.get().doTest(true); }
  @Test public void testHeredoc()       { parsingFixture.get().doTest(true); }
  @Test public void testHereEof()       { parsingFixture.get().doTest(true); }
  @Test public void testIf()            { parsingFixture.get().doTest(true); }
  @Test public void testConditional()   { parsingFixture.get().doTest(true); }
  @Test public void testBacktick()      { parsingFixture.get().doTest(true); }
  @Test public void testFunction()      { parsingFixture.get().doTest(true); }
  @Test public void testTrap()          { parsingFixture.get().doTest(true); }
  @Test public void testMacports()      { parsingFixture.get().doTest(true); }
  @Test public void testParams()        { parsingFixture.get().doTest(true); }
  @Test public void testDict()          { parsingFixture.get().doTest(true); }
  @Test public void testRecover()       { parsingFixture.get().doTest(true); }
  @Test public void testGit()           { parsingFixture.get().doTest(true); }
  @Test public void testGaudi()         { parsingFixture.get().doTest(true); }
  @Test public void testPipeline()      { parsingFixture.get().doTest(true); }
  @Test public void testCommands()      { parsingFixture.get().doTest(true); }
  @Test public void testRegex()         { parsingFixture.get().doTest(true); }
  @Test public void testRegex2()        { parsingFixture.get().doTest(true); }
  @Test public void testRedirection()   { parsingFixture.get().doTest(true); }
  @Test public void testRedirection2()  { parsingFixture.get().doTest(true); }
  @Test public void testSource()        { parsingFixture.get().doTest(true); }
  @Test public void testRedirectErrors(){ parsingFixture.get().doTest(true); }
  @Test public void testIdea263122()    { parsingFixture.get().doTest(true); } // IDEA-263122
  @Test public void testIdea244342()    { parsingFixture.get().doTest(true); } // IDEA-244342
  @Test public void testIdea279283()    { parsingFixture.get().doTest(true); } // IDEA-279283
  @Test public void testInstallJdk()    { parsingFixture.get().doTest(true); }
  @Test public void testTest(){ parsingFixture.get().doTest(true); }
}
