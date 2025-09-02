// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.util.ThrowableRunnable;
import com.jetbrains.python.fixtures.PyTestCase;
import org.jetbrains.annotations.NotNull;


public class PyCopyPasteTest extends PyTestCase {
  @Override
  protected void runTestRunnable(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
    CodeInsightSettings.runWithTemporarySettings(settings -> {
      settings.INDENT_TO_CARET_ON_PASTE = true;
      super.runTestRunnable(testRunnable);
      return null;
    });
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

  // PY-18522
  public void testSameIndentPreserved() {
    doTest();
  }

  // PY-18522
  public void testEmptyFunctionCaretAtNoneIndent() {
    doTest();
  }
  
  // PY-18522
  public void testEmptyFunctionCaretAtDefIndent() {
    doTest();
  }

  // PY-18522
  public void testEmptyFunctionCaretAtBodyIndent() {
    doTest();
  }

  public void testEmptyFunctionCaretAtEndOfFile() {
    doTest();
  }
  
  // PY-19053
  public void testSimpleExpressionPartCaretAtLineEnd() {
    doTest();
  }

  // PY-18522
  public void testEmptyBranchBlock() {
    doTest();
  }

  // PY-18522
  public void testEmptyParentBlockWithCommentInside() {
    doTest();
  }

  // PY-19064
  public void testAmbiguousParentBlockSmallestIndent() {
    doTest();
  }
  
  // PY-19064
  public void testAmbiguousParentBlockLargestIndent() {
    doTest();
  }
  
  // PY-19064
  public void testAmbiguousParentBlockMidIndent() {
    doTest();
  }

  // PY-19100
  public void testTopLevelFunctionWithMultilineParameterList() {
    doTest();
  }

  // PY-19100
  public void testTopLevelIfStatementWithMultilineCondition() {
    doTest();
  }

  // PY-19100
  public void testTryBlockWithBadSelection() {
    doTest();
  }

  // PY-19100
  public void testAsyncFunctionWithBadSelection() {
    doTest();
  }

  // PY-20138
  public void testUseExistingIndentWhenCaretAtFirstColumn() {
    doTest();
  }
  
  // PY-20138
  public void testUseExistingIndentWhenCaretAtFirstColumnEndOfFile() {
    doTest();
  }
  
  // PY-20138
  public void testInvalidExistingIndentWhenCaretAtFirstColumn() {
    doTest();
  }

  // PY-22563
  public void testBeginningOfIndentedLineSelectedAndReplacedWithWord() {
    doTest();
  }

  // PY-22563
  public void testWholeIndentedLineSelectedWithoutIndentAndReplacedWithWord() {
    doTest();
  }

  // PY-22563
  public void testWholeIndentedLineSelectedWithIndentAndReplacedWithWord() {
    doTest();
  }
  
  // PY-22563
  public void testWholeIndentedLineSelectedWithPartialIndentAndReplacedWithWord() {
    doTest();
  }

  // PY-29506
  public void testBeginningOfIndentedLinePrecededByPastedWord() {
    doTest();
  }
}
