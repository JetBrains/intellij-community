package com.jetbrains.python;

import com.intellij.codeInsight.editorActions.moveUpDown.MoveStatementDownAction;
import com.intellij.codeInsight.editorActions.moveUpDown.MoveStatementUpAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;

/**
 * @author Alexey.Ivanov
 */
public class PyStatementMoverTest extends PyLightFixtureTestCase {
  private void doTest() {
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

  public void testSimple() {
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

  public void testSelection() {
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
}
