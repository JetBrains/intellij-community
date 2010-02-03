package com.jetbrains.python.refactoring;

import com.jetbrains.python.fixtures.PyLightFixtureTestCase;

/**
 * @author yole
 */
public class PyRenameTest extends PyLightFixtureTestCase {
  public void testRenameField() throws Exception {  // PY-457
    myFixture.configureByFile("refactoring/rename/" + getTestName(true) + ".py");
    myFixture.renameElementAtCaret("qu");
    myFixture.checkResultByFile("refactoring/rename/" + getTestName(true) + "_after.py");
  }
}
