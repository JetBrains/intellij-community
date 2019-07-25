// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.wrap;

import com.intellij.application.options.CodeStyle;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.intellij.plugins.markdown.MarkdownTestingUtil;
import org.intellij.plugins.markdown.lang.MarkdownLanguage;
import org.jetbrains.annotations.NotNull;

public class MarkdownWrapTest extends BasePlatformTestCase {
  private boolean myOldWrap;
  private int myOldMargin;

  @Override
  protected String getTestDataPath() {
    return MarkdownTestingUtil.TEST_DATA_PATH + "/wrap";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final CodeStyleSettings settings = CodeStyle.getSettings(myFixture.getProject());
    final CommonCodeStyleSettings commonCodeStyleSettings = settings.getCommonSettings(MarkdownLanguage.INSTANCE);
    myOldWrap = settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN;
    myOldMargin = commonCodeStyleSettings.RIGHT_MARGIN;
    settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = true;
    commonCodeStyleSettings.RIGHT_MARGIN = 80;
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      final CodeStyleSettings settings = CodeStyle.getSettings(myFixture.getProject());
      final CommonCodeStyleSettings settingsCommonSettings = settings.getCommonSettings(MarkdownLanguage.INSTANCE);
      settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = myOldWrap;
      settingsCommonSettings.RIGHT_MARGIN = myOldMargin;
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testWrapInList() {
    doTest(" a b c");
  }

  public void testWrapInQuotes() {
    doTest("synchronization");
  }

  public void testWrapInCodeFence() {
    doTest("a b c");
  }

  public void testWrapInCodeFenceInQuotes() {
    doTest("a b c d e f");
  }

  public void testWrapInCodeFenceInList() {
    doTest("synchronization");
  }

  public void testWrapInHeader() {
    doTest("synchronization");
  }


  public void testWrapInTable() {
    doTest("synchronization");
  }

  public void testWrapRightMargin() {
    final CodeStyleSettings settings = CodeStyle.getSettings(myFixture.getProject());
    final CommonCodeStyleSettings commonCodeStyleSettings = settings.getCommonSettings(MarkdownLanguage.INSTANCE);
    int oldValue = commonCodeStyleSettings.RIGHT_MARGIN;
    boolean oldMarginValue = settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN;
    commonCodeStyleSettings.RIGHT_MARGIN = 100;
    settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = true;
    try {
      final String testName = getTestName(true);
      myFixture.configureByFile(testName + ".md");
      for (int i = 0; i != 45; ++i) {
        myFixture.type(' ');
      }
      myFixture.checkResultByFile(testName + ".after.md");
    }
    finally {
      commonCodeStyleSettings.RIGHT_MARGIN = oldValue;
      settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = oldMarginValue;
    }
  }

  private void doTest(@NotNull String textToType) {
    String testName = getTestName(true);
    myFixture.configureByFile(testName + ".md");
    myFixture.type(textToType);
    myFixture.checkResultByFile(testName + ".after.md");
  }
}