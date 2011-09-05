package com.jetbrains.python.refactoring;

import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.refactoring.introduce.IntroduceHandler;
import com.jetbrains.python.refactoring.introduce.variable.PyIntroduceVariableHandler;

/**
 * @author yole
 */
@TestDataPath("$CONTENT_ROOT/../testData/refactoring/introduceVariable/")
public class PyIntroduceVariableTest extends PyIntroduceTestCase {
  public void testSimple() {
    doTest();
  }

  public void testPy995() {
    doTest();
  }

  public void testSkipLeadingWhitespace() {  // PY-1338
    doTest();    
  }

  public void testPy2862() {
    doTest();
  }

  public void testSuggestKeywordArgumentName() {   // PY-1260
    doTestSuggestions(PyExpression.class, "extra_context");
  }

  public void testSuggestArgumentName() {   // PY-1260
    doTestSuggestions(PyExpression.class, "extra_context");
  }

  public void testSuggestTypeName() {  // PY-1336
    doTestSuggestions(PyCallExpression.class, "my_class");
  }

  public void testSuggestStringConstantValue() { // PY-1276
    doTestSuggestions(PyExpression.class, "foo_bar");
  }
  
  public void testIncorrectSelection() {  // PY-4455
    boolean thrownExpectedException = false;
    try {
      doTest();
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      if (e.getMessage().equals("Cannot perform refactoring using selected element(s)")) {
        thrownExpectedException = true;
      }
    }
    assertTrue(thrownExpectedException);
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/refactoring/introduceVariable";
  }

  protected IntroduceHandler createHandler() {
    return new PyIntroduceVariableHandler();
  }
}
