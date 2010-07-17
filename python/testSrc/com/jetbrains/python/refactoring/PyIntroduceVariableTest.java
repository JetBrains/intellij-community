package com.jetbrains.python.refactoring;

import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.refactoring.introduce.variable.VariableIntroduceHandler;

import java.util.Collection;

/**
 * @author yole
 */
public class PyIntroduceVariableTest extends PyLightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/refactoring/introduceVariable";
  }

  public void testSimple() {
    doTest();
  }

  public void testPy995() {
    doTest();
  }

  public void testSuggestKeywordArgumentName() {   // PY-1260
    myFixture.configureByFile(getTestName(true) + ".py");
    VariableIntroduceHandler handler = new VariableIntroduceHandler();
    PyExpression expr = PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset()), PyExpression.class);
    final Collection<String> names = handler.getSuggestedNames(expr);
    assertTrue(names.contains("extra_context"));
  }

  public void testSuggestArgumentName() {   // PY-1260
    myFixture.configureByFile(getTestName(true) + ".py");
    VariableIntroduceHandler handler = new VariableIntroduceHandler();
    PyExpression expr = PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset()), PyExpression.class);
    final Collection<String> names = handler.getSuggestedNames(expr);
    assertTrue(names.contains("extra_context"));
  }

  private void doTest() {
    myFixture.configureByFile(getTestName(true) + ".py");
    VariableIntroduceHandler handler = new VariableIntroduceHandler();
    handler.performAction(myFixture.getProject(),  myFixture.getEditor(), myFixture.getFile(), "a", true, false, false);
    myFixture.checkResultByFile(getTestName(true) + ".after.py");
  }
}
