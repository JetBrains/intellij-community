/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.jetbrains.python.fixtures.PyTestCase;

/**
 * @author yole
 */
public class PyWrapTest extends PyTestCase {
  private boolean myOldWrap;
  private int myOldMargin;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final CodeStyleSettings settings = CodeStyleSettingsManager.getInstance(myFixture.getProject()).getCurrentSettings();
    final CommonCodeStyleSettings pythonSettings = settings.getCommonSettings(PythonLanguage.getInstance());
    myOldWrap = settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN;
    myOldMargin = pythonSettings.RIGHT_MARGIN;
    settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = true;
    pythonSettings.RIGHT_MARGIN = 80;
  }

  @Override
  protected void tearDown() throws Exception {
    final CodeStyleSettings settings = CodeStyleSettingsManager.getInstance(myFixture.getProject()).getCurrentSettings();
    final CommonCodeStyleSettings pythonSettings = settings.getCommonSettings(PythonLanguage.getInstance());
    settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = myOldWrap;
    pythonSettings.RIGHT_MARGIN = myOldMargin;
    super.tearDown();
  }

  public void testBackslashOnWrap() {
    doTest("and hasattr(old_node, attr):");
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
    final CodeStyleSettings settings = CodeStyleSettingsManager.getInstance(myFixture.getProject()).getCurrentSettings();
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
