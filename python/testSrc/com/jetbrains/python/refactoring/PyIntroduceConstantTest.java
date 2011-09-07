package com.jetbrains.python.refactoring;

import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.refactoring.introduce.IntroduceHandler;
import com.jetbrains.python.refactoring.introduce.constant.PyIntroduceConstantHandler;

/**
 * @author yole
 */
@TestDataPath("$CONTENT_ROOT/../testData/refactoring/introduceConstant/")
public class PyIntroduceConstantTest extends PyIntroduceTestCase {
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

  public void testSuggestUniqueNames() {  // PY-4409
    doTestSuggestions(PyExpression.class, "S1");
  }
  
  public void testSuggestUniqueNamesGlobalScope() {  // PY-4409
    doTestSuggestions(PyExpression.class, "S1");
  }

  public void testPy4414() {
    doTestInplace(null);
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/refactoring/introduceConstant";
  }

  @Override
  protected IntroduceHandler createHandler() {
    return new PyIntroduceConstantHandler();
  }
}
