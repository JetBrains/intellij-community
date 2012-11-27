package com.jetbrains.python.refactoring;

import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.refactoring.introduce.IntroduceHandler;
import com.jetbrains.python.refactoring.introduce.parameter.PyIntroduceParameterHandler;

/**
 * User: ktisha
 */
@TestDataPath("$CONTENT_ROOT/../testData/refactoring/introduceParameter/")
public class PyIntroduceParameterTest extends PyIntroduceTestCase {
  public void testSimple() {
    doTest();
  }

  public void testReferencedParameter() {
    doTestCannotPerform(PyBundle.message("refactoring.introduce.selection.error"));
  }

  public void testParameter() {
    doTest();
  }

  public void testKwParameter() {
    doTest();
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/refactoring/introduceParameter";
  }

  protected IntroduceHandler createHandler() {
    return new PyIntroduceParameterHandler();
  }

  private void doTestCannotPerform(String expected) {
    try {
      doTest();
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      assertEquals(expected, e.getMessage());
    }
  }
}
