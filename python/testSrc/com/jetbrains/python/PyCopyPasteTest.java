package com.jetbrains.python;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.openapi.actionSystem.IdeActions;
import com.jetbrains.python.fixtures.PyTestCase;

/**
 * @author yole
 */
public class PyCopyPasteTest extends PyTestCase {
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

  public void testIndent3() {
    doTest();
  }

  public void testIndentIncrease() {
    doTest();
  }

  public void testSingleLine() {
    doTest();
  }

  public void testMethodInClass() {
    doTest();
  }

  public void testIndent11() {
    doTestSingleLine();
  }

  public void testIndent12() {
    doTestSingleLine();
  }

  public void testIndent13() {
    doTestSingleLine();
  }

  public void testIndent21() {
    doTestSingleLine();
  }

  public void testIndent22() {
    doTestSingleLine();
  }

  public void testIndent23() {
    doTestSingleLine();
  }

  public void testIndent31() {
    doTestSingleLine();
  }

  public void testIndent32() {
    doTestSingleLine();
  }

  public void testIndent33() {
    doTestSingleLine();
  }

  public void testIndent41() {
    doTestSingleLine();
  }

  public void testIndent42() {
    doTestSingleLine();
  }

  public void testIndent43() {
    doTestSingleLine();
  }

  public void testIndentMulti11() {
    doTestMultiLine();
  }

  public void testIndentMulti12() {
    doTestMultiLine();
  }

  public void testIndentMulti13() {
    doTestMultiLine();
  }

  public void testIndentMulti21() {
    doTestMultiLine();
  }

  public void testIndentMulti22() {
    doTestMultiLine();
  }

  public void testIndentMulti23() {
    doTestMultiLine();
  }

  public void testIndentMulti31() {
    doTestMultiLine();
  }

  public void testIndentMulti32() {
    doTestMultiLine();
  }

  public void testIndentMulti33() {
    doTestMultiLine();
  }

  public void testIndentMulti41() {
    doTestMultiLine();
  }

  public void testIndentMulti42() {
    doTestMultiLine();
  }

  public void testIndentMulti43() {
    doTestMultiLine();
  }

  public void testIndentInnerFunction() {
    doTestMultiLine();
  }

  private void doTest() {
    String name = getTestName(false);

    myFixture.configureByFile("copyPaste/" + name + ".src.py");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_COPY);
    myFixture.configureByFile("copyPaste/" + name + ".dst.py");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PASTE);
    myFixture.checkResultByFile("copyPaste/" + name + ".after.py");
  }

  private void doTestSingleLine() {
    String name = getTestName(false);

    myFixture.configureByFile("copyPaste/singleLine/" + name + ".src.py");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_COPY);
    myFixture.configureByFile("copyPaste/singleLine/" + name + ".dst.py");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PASTE);
    myFixture.checkResultByFile("copyPaste/singleLine/" + name + ".after.py");
  }

  private void doTestMultiLine() {
    String name = getTestName(false);

    myFixture.configureByFile("copyPaste/multiLine/" + name + ".src.py");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_COPY);
    myFixture.configureByFile("copyPaste/multiLine/" + name + ".dst.py");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PASTE);
    myFixture.checkResultByFile("copyPaste/multiLine/" + name + ".after.py");
  }
}
