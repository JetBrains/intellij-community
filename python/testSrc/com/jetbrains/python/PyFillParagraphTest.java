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

import com.intellij.codeInsight.editorActions.fillParagraph.FillParagraphAction;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.fixtures.PyTestCase;

/**
 * User : ktisha
 */
public class PyFillParagraphTest extends PyTestCase {

  public void testDocstring() {
    doTest();
  }

  public void testMultilineDocstring() {
    doTest();
  }

  public void testDocstringOneParagraph() {
    doTest();
  }

  public void testString() {
    doTestWithParenthesizeOnEnter(false, 120);
  }

  public void testComment() {
    doTest();
  }

  public void testCommentSecondParagraph() {
    doTest();
  }

  public void testPrefixPostfix() {
    doTest();
  }

  public void testSingleLine() {
    doTest();
  }

  public void testEmptyMultilineString() {
    doTest();
  }

  public void testEnter() {
    doTestWithMargin(80);
  }

  // PY-26422
  public void testFString() {
    doTestWithParenthesizeOnEnter(false, 20);
  }

  private void doTest() {
    doTestWithMargin(120);
  }

  private void doTestWithMargin(int margin) {
    getCodeStyleSettings().setRightMargin(PythonLanguage.INSTANCE, margin);
    String baseName = "/fillParagraph/" + getTestName(true);
    myFixture.configureByFile(baseName + ".py");
    myFixture.testAction(new FillParagraphAction());
    myFixture.checkResultByFile(baseName + "_after.py", true);
  }

  private void doTestWithParenthesizeOnEnter(boolean enabled, int margin) {
    boolean initialValue = PyCodeInsightSettings.getInstance().PARENTHESISE_ON_ENTER;
    try {
      PyCodeInsightSettings.getInstance().PARENTHESISE_ON_ENTER = enabled;
      doTestWithMargin(margin);
    } finally {
      PyCodeInsightSettings.getInstance().PARENTHESISE_ON_ENTER = initialValue;
    }
  }
}
