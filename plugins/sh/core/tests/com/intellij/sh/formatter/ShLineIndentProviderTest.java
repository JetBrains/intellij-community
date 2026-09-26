// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.formatter;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class ShLineIndentProviderTest extends BasePlatformTestCase {
  private static final String FILE_EXTENSION = ".sh";
  private static final String AFTER_FILE_EXTENSION = ".after.sh";
  private static final char KEY_ENTER = '\n';

  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("sh") + "/core/testData/formatter";
  }

  public void testShebang()               { doTest(); }
  public void testEmptyFunction()         { doTest(); }
  public void testFunctionWithBody()      { doTest(); }
  public void testFunctionBodyEnd()       { doTest(); }
  public void testEmptyIf()               { doTest(); }
  public void testIfWithElif()            { doTest(); }
  public void testIfWithElse()            { doTest(); }
  public void testIfEnd()                 { doTest(); }
  public void testWhile()                 { doTest(); }
  public void testUntil()                 { doTest(); }
  public void testFor()                   { doTest(); }
  public void testCaseClause()            { doTest(); }
  public void testCaseEnd()               { doTest(); }
  public void testCasePattern()           { doTest(); }
  public void testIndentAtBeginOfFile()   { doTest(); }
  public void testInternationalization()  { doTest(); }

  private void doTest() {
    String testName = getTestName(true);
    myFixture.configureByFile(testName + FILE_EXTENSION);
    myFixture.type(KEY_ENTER);
    myFixture.checkResultByFile(testName + AFTER_FILE_EXTENSION);
  }
}