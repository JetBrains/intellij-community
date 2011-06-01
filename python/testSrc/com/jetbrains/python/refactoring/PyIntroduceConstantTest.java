package com.jetbrains.python.refactoring;

import com.jetbrains.python.fixtures.PyLightFixtureTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.refactoring.introduce.constant.ConstantIntroduceHandler;

/**
 * @author yole
 */
public class PyIntroduceConstantTest extends PyLightFixtureTestCase {
  public void testPy1840() {
    doTest();
  }

  public void testPy1840EntireLine() {
    setLanguageLevel(LanguageLevel.PYTHON30);
    try {
      doTest();
    }
    finally {
      setLanguageLevel(null);
    }
  }

  public void testInsertAfterImport() {  // PY-2149
    doTest();
  }

  public void testInsertAfterDocstring() { // PY-3657
    doTest();
  }

  private void doTest() {
    myFixture.configureByFile("/refactoring/introduceConstant/" + getTestName(true) + ".py");
    ConstantIntroduceHandler handler = new ConstantIntroduceHandler();
    handler.performAction(myFixture.getProject(),  myFixture.getEditor(), myFixture.getFile(), "a", true, false, false);
    myFixture.checkResultByFile("/refactoring/introduceConstant/" + getTestName(true) + ".after.py");
  }
}
