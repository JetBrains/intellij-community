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

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
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

  public void testSelection1() { //PY-6994
    doTest();
  }

  public void testSelection2() { //PY-6994
    doTest();
  }

  public void testSelection3() { //PY-6994
    doTest();
  }

  public void testSelectionReverse1() { //PY-6994
    doTest();
  }

  public void testSelectionReverse2() { //PY-6994
    doTest();
  }

  public void testSelectionReverse3() { //PY-6994
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

  public void testIndentInnerFunction1() {  //PY-6887
    doTestMultiLine();
  }

  public void testIndentInnerFunction2() {  //PY-6886
    doTestMultiLine();
  }

  public void testIndentFunction() {
    doTestMultiLine();
  }

  public void testDecreaseIndent() {    //PY-6889
    doTestMultiLine();
  }

  public void testIndentTryExcept() {    //PY-6907
    doTestMultiLine();
  }

  public void testIndentIfElse() {    //PY-6907
    doTestMultiLine();
  }

  public void testIndentWithEmptyLine() {    //PY-6884
    doTestMultiLine();
  }

  public void testIndentOnTopLevel() {    //PY-6928
    doTestSingleLine();
  }

  public void testIndentInIfInDef() {    //PY-6927
    doTestSingleLine();
  }

  public void testIndentTopLevel() {    //PY-6889
    doTestMultiLine();
  }

  public void testTheSamePlace() {    //PY-6907
    doTest();
  }

  public void testWhitespace() {    //PY-6966
    doTest();
  }

  public void testUnfinishedCompound() {    //PY-6965
    doTest();
  }

  public void testNonRectangleTopLevel() {    //PY-6995
    doTest();
  }

  public void testLineToEnd() {    //PY-7524
    doTest();
  }

  public void testLineToPrev() {    //PY-7524
    doTest();
  }

  public void testLineToBegin() {    //PY-7524
    doTest();
  }

  public void testSelectionOneLine() {    //PY-7470
    doTest();
  }

  public void testSelectionOneLine1() {    //PY-7470
    doTest();
  }

  public void testSelectionOneLine2() {    //PY-7470
    doTest();
  }

  public void testSelectionOneLine3() {    //PY-7470
    doTest();
  }

  public void testBeginningOfFile() {    //PY-7524
    doTest();
  }

  public void testTwoIndentedLines() {    //PY-8693
    doTest();
  }

  public void testReplaceSelection() {    //PY-8744
    doTest();
  }

  public void testDictionary() {    //PY-8875
    doTest();
  }

  public void testIndentTab() {
    doTestTabs();
  }

  public void testIndent8982() {
    doTest();
  }

  public void testIndent7709() {
    doTest();
  }

  public void testIndent6994() {
    doTest();
  }

  public void testIndentBeforeElse() {
    doTest();
  }

  public void testEmpty() {
    doTest();
  }

  public void testInnerToOuterFunction() {
    doTest();
  }

  public void testEmptyLineInList() {
    doTest();
  }

  public void testCaretAtTheBeginningOfIndent() {
    doTest();
  }

  public void testPasteToStringLiteral() {
    doTest();
  }

  private void doTestTabs() {
    final CommonCodeStyleSettings.IndentOptions indentOptions = getCodeStyleSettings().getIndentOptions(PythonFileType.INSTANCE);
    indentOptions.USE_TAB_CHARACTER = true;
    try {
      doTest();
    }
    finally {
      indentOptions.USE_TAB_CHARACTER = false;
    }
  }

  public void testIndentTabIncrease() {
    doTestTabs();
  }

  private void doTest(String prefix) {
    int oldReformat = CodeInsightSettings.getInstance().REFORMAT_ON_PASTE;
    try {
      CodeInsightSettings.getInstance().REFORMAT_ON_PASTE = CodeInsightSettings.NO_REFORMAT;
      String name = getTestName(false);

      myFixture.configureByFile("copyPaste/" + prefix + name + ".src.py");
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_COPY);
      myFixture.configureByFile("copyPaste/" + prefix + name + ".dst.py");
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PASTE);
      myFixture.checkResultByFile("copyPaste/" + prefix + name + ".after.py", true);
    }
    finally {
      CodeInsightSettings.getInstance().REFORMAT_ON_PASTE = oldReformat;
    }

  }

  private void doTest() {
    doTest("");
  }

  private void doTestSingleLine() {
    doTest("singleLine/");
  }

  private void doTestMultiLine() {
    doTest("multiLine/");
  }
}
