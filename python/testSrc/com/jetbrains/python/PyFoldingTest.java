package com.jetbrains.python;

import com.jetbrains.python.fixtures.PyTestCase;

/**
 * @author yole
 */
public class PyFoldingTest extends PyTestCase {
  private void doTest() {
    myFixture.testFolding(getTestDataPath() + "/folding/" + getTestName(true) + ".py");
  }

  public void testClassTrailingSpace() {  // PY-2544
    doTest();
  }

  public void testDocString() {
    doTest();
  }
}
