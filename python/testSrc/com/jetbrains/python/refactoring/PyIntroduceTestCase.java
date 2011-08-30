package com.jetbrains.python.refactoring;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.refactoring.introduce.IntroduceHandler;
import com.jetbrains.python.refactoring.introduce.IntroduceOperation;

import java.util.Collection;

/**
 * @author yole
 */
public abstract class PyIntroduceTestCase extends PyLightFixtureTestCase {
  protected void doTestSuggestions(Class<? extends PyExpression> parentClass, String... expectedNames) {
    myFixture.configureByFile(getTestName(true) + ".py");
    IntroduceHandler handler = createHandler();
    PyExpression expr = PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset()),
                                                    parentClass);
    final Collection<String> names = handler.getSuggestedNames(expr);
    for (String expectedName : expectedNames) {
      assertTrue(StringUtil.join(names, ", "), names.contains(expectedName));
    }
  }

  protected abstract IntroduceHandler createHandler();

  protected void doTest() {
    myFixture.configureByFile(getTestName(true) + ".py");
    IntroduceHandler handler = createHandler();
    handler.performAction(createOperation());
    myFixture.checkResultByFile(getTestName(true) + ".after.py");
  }

  protected IntroduceOperation createOperation() {
    return new IntroduceOperation(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), "a", true, false, false);
  }
}
