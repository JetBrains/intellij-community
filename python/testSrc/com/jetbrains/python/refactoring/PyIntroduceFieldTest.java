package com.jetbrains.python.refactoring;

import com.jetbrains.python.fixtures.PyLightFixtureTestCase;
import com.jetbrains.python.refactoring.introduce.IntroduceHandler;
import com.jetbrains.python.refactoring.introduce.IntroduceOperation;
import com.jetbrains.python.refactoring.introduce.field.PyIntroduceFieldHandler;

/**
 * @author yole
 */
public class PyIntroduceFieldTest extends PyLightFixtureTestCase {
  public void testMetaClass() {  // PY-1580
    doTest(IntroduceHandler.InitPlace.SAME_METHOD);
  }

  public void testInConstructor() {  // PY-1983
    doTest(IntroduceHandler.InitPlace.CONSTRUCTOR);
  }

  public void testVariableToField() {
    doTest(IntroduceHandler.InitPlace.CONSTRUCTOR);
  }

  private void doTest(IntroduceHandler.InitPlace initPlace) {
    myFixture.configureByFile("/refactoring/introduceField/" + getTestName(true) + ".py");
    PyIntroduceFieldHandler handler = new PyIntroduceFieldHandler();
    final IntroduceOperation introduceOperation = new IntroduceOperation(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), "a", true, false, false);
    introduceOperation.setInitPlace(initPlace);
    handler.performAction(introduceOperation);
    myFixture.checkResultByFile("/refactoring/introduceField/" + getTestName(true) + ".after.py");
  }
}
