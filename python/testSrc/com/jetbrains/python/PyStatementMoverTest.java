package com.jetbrains.python;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;

/**
 * @author Alexey.Ivanov
 */
public class PyStatementMoverTest extends PyTestCase {
  private void doTest() {
    final String testName = getTestName(true);
    myFixture.configureByFile("mover/" + testName + ".py");
    myFixture.performEditorAction(IdeActions.ACTION_MOVE_STATEMENT_UP_ACTION);
    myFixture.checkResultByFile("mover/" + testName + "_afterUp.py", true);

    FileDocumentManager.getInstance().reloadFromDisk(myFixture.getDocument(myFixture.getFile()));
    myFixture.configureByFile("mover/" + getTestName(true) + ".py");
    myFixture.performEditorAction(IdeActions.ACTION_MOVE_STATEMENT_DOWN_ACTION);
    myFixture.checkResultByFile("mover/" + testName + "_afterDown.py", true);
  }

  public void testSimple() {
    doTest();
  }

  public void testCommentUp() {
    doTest();
  }

  public void testTryExcept() {
    doTest();
  }

  public void testInnerIf() {
    doTest();
  }

  public void testNestedIfUp() {
    doTest();
  }

  public void testCommentOut() {  //PY-5527
    doTest();
  }

  public void testMoveDownOut() {
    doTest();
  }

  public void testIndentedOneLine() { //PY-5268
    doTest();
  }

  public void testComment() {   //PY-5270
    doTest();
  }

  public void testOneStatementInFunction() {
    doTest();
  }

  public void testOutsideStatement() {
    doTest();
  }

  public void testInsideStatement() {
    doTest();
  }

  public void testFunctions() {
    doTest();
  }

  public void testBetweenStatementParts() {
    doTest();
  }

  public void testMoveStatement() {
    doTest();
  }

  public void testDoubleIf() {
    doTest();
  }

  public void testOneStatementInClass() {
    doTest();
  }

  public void testMoveOut() {
    doTest();
  }

  public void testSimpleBlankLines() {
    doTest();
  }

  public void testPy950() {
    doTest();
  }

  public void testIndent() {
    doTest();
  }

  public void testDecorator() {
    doTest();
  }

  public void testLastLine() { // PY-5017
    doTest();
  }

  public void testNestedBlock() { // PY-1343
    doTest();
  }

  public void testNestedBlockDown() { // PY-5221
    doTest();
  }

  public void testFunctionDown() { // PY-5195
    doTest();
  }

  public void testContinueBreak() { // PY-5193
    doTest();
  }

  public void testNestedTry() { // PY-5192
    doTest();
  }

  public void testUpInNested() { // PY-5192
    doTest();
  }

  public void testClass() { // PY-5196
    doTest();
  }

  public void testOneLineCompound() { // PY-5198
    doTest();
  }

  public void testEmptyLine() { // PY-5197
    doTest();
  }

  public void testDocstring() { // PY-5203
    doTest();
  }

  public void testOneLineCompoundOutside() { // PY-5201
    doTest();
  }

  public void testFunctionBlock() { // PY-6163
    doTest();
  }

  public void testCommentIntoCompound() { // PY-6133
    doTest();
  }

  public void testWith() { // PY-5202
    try {
      setLanguageLevel(LanguageLevel.PYTHON27);
      doTest();
    } finally {
      setLanguageLevel(null);
    }
  }
}
