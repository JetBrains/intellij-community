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
