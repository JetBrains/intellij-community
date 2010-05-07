package com.jetbrains.python;

import com.intellij.codeInsight.editorActions.moveUpDown.MoveStatementDownAction;
import com.intellij.codeInsight.editorActions.moveUpDown.MoveStatementUpAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   05.05.2010
 * Time:   18:20:20
 */
public class PyStatementMoverTest extends PyLightFixtureTestCase {
  private void doTest() throws Exception {
    final String testName = getTestName(true);
    myFixture.configureByFile("mover/" + testName + ".py");
    performAction(new MoveStatementUpAction().getHandler());
    myFixture.checkResultByFile("mover/" + testName + "_afterUp.py", true);
    
    FileDocumentManager.getInstance().reloadFromDisk(myFixture.getDocument(myFixture.getFile()));
    myFixture.configureByFile("mover/" + getTestName(true) + ".py");
    performAction(new MoveStatementDownAction().getHandler());
    myFixture.checkResultByFile("mover/" + testName + "_afterDown.py", true);
  }

  private void performAction(EditorActionHandler handler) {
    final Editor editor = myFixture.getEditor();
    if (handler.isEnabled(editor, null)) {
      handler.execute(editor, null);
    }
  }

  public void testSimple() throws Exception {
    doTest();
  }

  public void testOutsideStatement() throws Exception {
    doTest();
  }

  public void testInsideStatement() throws Exception {
    doTest();
  }

  public void testFunctions() throws Exception {
    doTest();
  }

  public void testBetweenStatementParts() throws Exception {
    doTest();
  }

  public void testMoveStatement() throws Exception {
    doTest();
  }

  public void testSelection() throws Exception {
    doTest();
  }

  public void testSimpleBlankLines() throws Exception {
    doTest();
  }

  public void testPY950() throws Exception {
    doTest();
  }
}
