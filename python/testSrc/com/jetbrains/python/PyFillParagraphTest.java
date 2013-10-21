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
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
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
    doTest();
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

  public void testEnter() {
    final CodeStyleSettings settings = CodeStyleSettingsManager.getInstance(myFixture.getProject()).getCurrentSettings();
    int oldValue = settings.RIGHT_MARGIN;
    settings.RIGHT_MARGIN = 80;
    try {
      doTest();
    }
    finally {
      settings.RIGHT_MARGIN = oldValue;
    }
  }

  private void doTest() {
    String baseName = "/fillParagraph/" + getTestName(true);
    myFixture.configureByFile(baseName + ".py");
    CommandProcessor.getInstance().executeCommand(myFixture.getProject(), new Runnable() {
      @Override
      public void run() {
        FillParagraphAction action = new FillParagraphAction();
        action.actionPerformed(new AnActionEvent(null, DataManager.getInstance().getDataContext(), "",
                                                 action.getTemplatePresentation(),
                                                 ActionManager.getInstance(), 0));
      }
    }, "", null);
    myFixture.checkResultByFile(baseName + "_after.py", true);
  }
}
