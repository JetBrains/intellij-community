package com.jetbrains.python;

import com.jetbrains.python.fixtures.PyLightFixtureTestCase;

/**
 * @author yole
 */
public class PyRegexpTest extends PyLightFixtureTestCase {
  public void testNestedCharacterClasses() {  // PY-2908
    doTest();
  }

  public void testNestedCharacterClasses2() {  // PY-2908
    doTest();
  }

  private void doTest() {
    myFixture.testHighlighting(true, false, false, "regexp/" + getTestName(true) + ".py");
  }
}
