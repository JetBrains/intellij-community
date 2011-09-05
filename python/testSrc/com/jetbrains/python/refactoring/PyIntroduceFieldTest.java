package com.jetbrains.python.refactoring;

import com.intellij.testFramework.TestDataPath;
import com.intellij.util.Consumer;
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
    doTest(new IntroduceOperationCustomizer(IntroduceHandler.InitPlace.SAME_METHOD));
  }

  public void testInConstructor() {  // PY-1983
    doTest(new IntroduceOperationCustomizer(IntroduceHandler.InitPlace.CONSTRUCTOR));
  }

  public void testVariableToField() {
    doTest(new IntroduceOperationCustomizer(IntroduceHandler.InitPlace.CONSTRUCTOR));
  }
  
  public void testUniqueName() {  // PY-4409
    doTestSuggestions(PyExpression.class, "s1");
  }

  @Override
  protected IntroduceHandler createHandler() {
    return new PyIntroduceFieldHandler();
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/refactoring/introduceField/";
  }

  private static class IntroduceOperationCustomizer implements Consumer<IntroduceOperation> {
    private final IntroduceHandler.InitPlace myPlace;

    private IntroduceOperationCustomizer(IntroduceHandler.InitPlace place) {
      myPlace = place;
    }

    @Override
    public void consume(IntroduceOperation operation) {
      operation.setInitPlace(myPlace);
    }
  }
}
