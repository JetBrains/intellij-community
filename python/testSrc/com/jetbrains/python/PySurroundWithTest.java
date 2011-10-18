package com.jetbrains.python;

import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.command.WriteCommandAction;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.refactoring.surround.surrounders.statements.PyWithIfSurrounder;
import com.jetbrains.python.refactoring.surround.surrounders.statements.PyWithTryExceptSurrounder;
import com.jetbrains.python.refactoring.surround.surrounders.statements.PyWithWhileSurrounder;

/**
 * @author yole
 */
public class PySurroundWithTest extends PyTestCase {
  public void testSurroundWithIf() throws Exception {
    doTest(new PyWithIfSurrounder());
  }

  public void testSurroundWithWhile() throws Exception {
    doTest(new PyWithWhileSurrounder());
  }

  public void _testSurroundWithTryExcept() throws Exception {
    doTest(new PyWithTryExceptSurrounder());
  }

  private void doTest(final Surrounder surrounder) throws Exception {
    String baseName = "/surround/" + getTestName(false);
    myFixture.configureByFile(baseName + ".py");
    new WriteCommandAction.Simple(myFixture.getProject()) {
      @Override
      protected void run() throws Throwable {
        SurroundWithHandler.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), surrounder);
      }
    }.execute();
    myFixture.checkResultByFile(baseName + "_after.py", true);
  }
}
