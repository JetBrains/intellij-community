package com.jetbrains.python.refactoring;

import com.jetbrains.python.fixtures.PyLightFixtureTestCase;
import com.jetbrains.python.refactoring.introduce.field.FieldIntroduceHandler;

/**
 * @author yole
 */
public class PyIntroduceFieldTest extends PyLightFixtureTestCase {
  public void testMetaClass() {  // PY-1580
    doTest();
  }

  private void doTest() {
    myFixture.configureByFile("/refactoring/introduceField/" + getTestName(true) + ".py");
    FieldIntroduceHandler handler = new FieldIntroduceHandler();
    handler.performAction(myFixture.getProject(),  myFixture.getEditor(), myFixture.getFile(), "a", true, false, false);
    myFixture.checkResultByFile("/refactoring/introduceField/" + getTestName(true) + ".after.py");
  }
}
