package com.jetbrains.python.refactoring;

import com.jetbrains.python.fixtures.PyLightFixtureTestCase;
import com.jetbrains.python.refactoring.introduce.variable.VariableIntroduceHandler;

/**
 * @author yole
 */
public class PyIntroduceVariableTest extends PyLightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/refactoring/introduceVariable";
  }

  public void testSimple() throws Exception {
    myFixture.configureByFile("simple.py");
    VariableIntroduceHandler handler = new VariableIntroduceHandler();
    handler.performAction(myFixture.getProject(),  myFixture.getEditor(), myFixture.getFile(), "a", true, false);
    myFixture.checkResultByFile("simple.after.py");
  }
}
