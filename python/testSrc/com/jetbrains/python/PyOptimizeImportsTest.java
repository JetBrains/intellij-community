package com.jetbrains.python;

import com.intellij.codeInsight.actions.OptimizeImportsAction;
import com.intellij.ide.DataManager;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;

/**
 * @author yole
 */
public class PyOptimizeImportsTest extends PyLightFixtureTestCase {
  public void testSimple() throws Exception {
    doTest();
  }

  public void testOneOfMultiple() throws Exception {
    doTest();
  }

  public void testImportStar() throws Exception {
    doTest();
  }

  public void testImportStarOneOfMultiple() throws Exception {
    doTest();
  }

  private void doTest() throws Exception {
    myFixture.configureByFile("optimizeImports/" + getTestName(true) + ".py");
    OptimizeImportsAction.actionPerformedImpl(DataManager.getInstance().getDataContext(myFixture.getEditor().getContentComponent()));
    myFixture.checkResultByFile("optimizeImports/" + getTestName(true) + ".after.py");
  }
}
