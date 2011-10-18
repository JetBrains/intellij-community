package com.jetbrains.python;

import com.intellij.codeInsight.actions.OptimizeImportsAction;
import com.intellij.ide.DataManager;
import com.jetbrains.python.fixtures.PyTestCase;

/**
 * @author yole
 */
public class PyOptimizeImportsTest extends PyTestCase {
  public void testSimple() {
    doTest();
  }

  public void testOneOfMultiple() {
    doTest();
  }

  public void testImportStar() {
    doTest();
  }

  public void testImportStarOneOfMultiple() {
    doTest();
  }

  public void testTryExcept() {
    doTest();
  }

  public void testFromFuture() {
    doTest();
  }

  public void testUnresolved() {  // PY-2201
    doTest();
  }

  private void doTest() {
    myFixture.configureByFile("optimizeImports/" + getTestName(true) + ".py");
    OptimizeImportsAction.actionPerformedImpl(DataManager.getInstance().getDataContext(myFixture.getEditor().getContentComponent()));
    myFixture.checkResultByFile("optimizeImports/" + getTestName(true) + ".after.py");
  }
}
