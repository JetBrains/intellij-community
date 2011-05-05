package com.jetbrains.python;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.util.SystemInfo;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;

/**
 * @author yole
 */
public class PyCopyPasteTest extends PyLightFixtureTestCase {
  private boolean myOldEnabled;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myOldEnabled = CodeInsightSettings.getInstance().INDENT_TO_CARET_ON_PASTE;
    CodeInsightSettings.getInstance().INDENT_TO_CARET_ON_PASTE = true;
  }

  @Override
  public void tearDown() throws Exception {
    CodeInsightSettings.getInstance().INDENT_TO_CARET_ON_PASTE = myOldEnabled;
    super.tearDown();
  }

  public void testIndent1() {
    doTest();
  }

  public void testIndent2() {
    doTest();
  }

  public void testIndentIncrease() {
    doTest();
  }

  public void testSingleLine() {
    doTest();
  }

  private void doTest() {
    if (!SystemInfo.isWindows) {
      System.out.println("System is not windows. Skipping.");
      return;
    }
    String name = getTestName(false);
    myFixture.configureByFile("copyPaste/" + name + ".src.py");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_COPY);
    myFixture.configureByFile("copyPaste/" + name + ".dst.py");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PASTE);
    myFixture.checkResultByFile("copyPaste/" + name + ".after.py");
  }
}
