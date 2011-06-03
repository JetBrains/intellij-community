package com.jetbrains.python;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;

/**
 * @author Alexey.Ivanov
 */
public class PyStatementMoverTest extends PyLightFixtureTestCase {
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
