package com.jetbrains.python.refactoring;

import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.refactoring.introduce.IntroduceHandler;
import com.jetbrains.python.refactoring.introduce.IntroduceOperation;
import com.jetbrains.python.refactoring.introduce.field.PyIntroduceFieldHandler;

/**
 * @author yole
 */
@TestDataPath("$CONTENT_ROOT/../testData/refactoring/introduceField/")
public class PyIntroduceFieldTest extends PyIntroduceTestCase {
  public void testMetaClass() {  // PY-1580
    doTest(IntroduceHandler.InitPlace.SAME_METHOD);
  }

  public void testInConstructor() {  // PY-1983
    doTest(IntroduceHandler.InitPlace.CONSTRUCTOR);
  }

  public void testVariableToField() {
    doTest(IntroduceHandler.InitPlace.CONSTRUCTOR);
  }
  
  public void testUniqueName() {  // PY-4409
    doTestSuggestions(PyExpression.class, "s1");
  }

  private void doTest(IntroduceHandler.InitPlace initPlace) {
    myFixture.configureByFile(getTestName(true) + ".py");
    PyIntroduceFieldHandler handler = new PyIntroduceFieldHandler();
    final IntroduceOperation introduceOperation = new IntroduceOperation(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), "a", true, false, false);
    introduceOperation.setInitPlace(initPlace);
    handler.performAction(introduceOperation);
    myFixture.checkResultByFile(getTestName(true) + ".after.py");
  }

  @Override
  protected IntroduceHandler createHandler() {
    return new PyIntroduceFieldHandler();
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/refactoring/introduceField/";
  }
}
