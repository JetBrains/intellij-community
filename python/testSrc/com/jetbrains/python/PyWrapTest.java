// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.fixtures.PyTestCase;


public class PyWrapTest extends PyTestCase {
  private boolean myOldWrap;
  private int myOldMargin;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final CodeStyleSettings settings = CodeStyle.getSettings(myFixture.getProject());
    final CommonCodeStyleSettings pythonSettings = settings.getCommonSettings(PythonLanguage.getInstance());
    myOldWrap = settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN;
    myOldMargin = pythonSettings.RIGHT_MARGIN;
    settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = true;
    pythonSettings.RIGHT_MARGIN = 80;
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      final CodeStyleSettings settings = CodeStyle.getSettings(myFixture.getProject());
      final CommonCodeStyleSettings pythonSettings = settings.getCommonSettings(PythonLanguage.getInstance());
      settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = myOldWrap;
      pythonSettings.RIGHT_MARGIN = myOldMargin;
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testBackslashOnWrap() {
    boolean initialValue = PyCodeInsightSettings.getInstance().PARENTHESISE_ON_ENTER;
    try {
      PyCodeInsightSettings.getInstance().PARENTHESISE_ON_ENTER = false;
      doTest("and hasattr(old_node, attr):");
    } finally {
      PyCodeInsightSettings.getInstance().PARENTHESISE_ON_ENTER = initialValue;
    }
  }

  public void testParenthesesOnWrap() {
    boolean initialValue = PyCodeInsightSettings.getInstance().PARENTHESISE_ON_ENTER;
    try {
      PyCodeInsightSettings.getInstance().PARENTHESISE_ON_ENTER = true;
      doTest("and hasattr(old_node, attr)");
    } finally {
      PyCodeInsightSettings.getInstance().PARENTHESISE_ON_ENTER = initialValue;
    }
  }

  public void testWrapInComment() {
    doTest("Aquitani");
  }

  public void testWrapInDocstring() {
    doTest("Aquitani");
  }

  public void testWrapInArgumentList() {
    doTest("=None");
  }

  // PY-4947
  public void testWrapInStringLiteral() {
    myFixture.setCaresAboutInjection(false);
    doTest(" AND field");
  }

  public void testWrapInInjectedStringLiteral() {
    myFixture.setCaresAboutInjection(false);
    myFixture.configureByFile("wrap/WrapInStringLiteral.py");

    final int stringLiteralOffset = 114;
    PsiFile hostFile = myFixture.getFile();
    assertNotNull(
      InjectedLanguageManager.getInstance(hostFile.getProject()).findInjectedElementAt(hostFile, stringLiteralOffset + "\"".length()));

    myFixture.type(" AND field");
    myFixture.checkResultByFile("wrap/WrapInStringLiteral.after.py", true);
  }

  public void testDontWrapStartOfString() { // PY-9436
    doTest("_some_long_text_here_to_test_right_margin_some_long_text_here_to_test_right_margin_some_long_text_here_to_test_right_margin");
  }


  public void testWrapRightMargin() {
    final CodeStyleSettings settings = CodeStyle.getSettings(myFixture.getProject());
    final CommonCodeStyleSettings pythonSettings = settings.getCommonSettings(PythonLanguage.getInstance());
    int oldValue = pythonSettings.RIGHT_MARGIN;
    boolean oldMarginValue = settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN;
    pythonSettings.RIGHT_MARGIN = 100;
    settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = true;
    try {
      final String testName = "wrap/" + getTestName(true);
      myFixture.configureByFile(testName + ".py");
      for (int i = 0; i != 45; ++i) {
        myFixture.type(' ');
      }
      myFixture.checkResultByFile(testName + ".after.py");
    }
    finally {
      pythonSettings.RIGHT_MARGIN = oldValue;
      settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = oldMarginValue;
    }
  }

  private void doTest(final String textToType) {
    myFixture.configureByFile("wrap/" + getTestName(false) + ".py");
    myFixture.type(textToType);
    myFixture.checkResultByFile("wrap/" + getTestName(false) + ".after.py", true);
  }
}
