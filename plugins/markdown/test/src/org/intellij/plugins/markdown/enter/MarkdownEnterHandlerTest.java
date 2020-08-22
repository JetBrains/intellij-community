// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.enter;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.intellij.plugins.markdown.MarkdownTestingUtil;

public class MarkdownEnterHandlerTest extends BasePlatformTestCase {
  @Override
  protected String getTestDataPath() {
    return MarkdownTestingUtil.TEST_DATA_PATH + "/enter";
  }

  public void testQuote() {
    doTest();
  }

  public void testCodeFence() {
    doTest();
  }

  public void testCodeFenceLang() {
    doTest();
  }

  public void testCodeFenceMiddle() {
    doTest();
  }

  public void testQuoteInListMiddle() {
    doTest();
  }

  public void testQuoteInList() {
    doTest();
  }

  public void testQuoteInList1() {
    doTest();
  }

  public void testQuoteInListAfterFirstLine() {
    doTest();
  }

  public void testQuoteInListWithSpace() {
    doTest();
  }

  public void testCodeFenceWithSpace() {
    doTest();
  }

  public void testQuoteInListMiddleWithSpace() {
    doTest();
  }

  private void doTest() {
    String testName = getTestName(true);
    myFixture.configureByFile(testName + ".before.md");
    myFixture.type("\n");
    myFixture.checkResultByFile(testName + ".after.md");
  }
}