package com.jetbrains.python.refactoring;

import com.jetbrains.python.fixtures.PyLightFixtureTestCase;
import com.jetbrains.python.refactoring.introduce.IntroduceHandler;
import com.jetbrains.python.refactoring.introduce.field.FieldIntroduceHandler;

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
    FieldIntroduceHandler handler = new FieldIntroduceHandler();
    handler.performAction(myFixture.getProject(),  myFixture.getEditor(), myFixture.getFile(), "a", initPlace, true, false, false);
    myFixture.checkResultByFile("/refactoring/introduceField/" + getTestName(true) + ".after.py");
  }
}
