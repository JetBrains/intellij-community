package com.jetbrains.python.refactoring;

import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.fixtures.LightMarkedTestCase;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.refactoring.inline.PyInlineLocalHandler;

import java.util.Map;

/**
 * @author Dennis.Ushakov
 */
public class PyInlineLocalTest extends LightMarkedTestCase {
  private void doTest() throws Exception {
    doTest(null);
  }

  private void doTest(String expectedError) throws Exception {
    final String name = getTestName(true);
    final Map<String,PsiElement> map = configureByFile("/refactoring/inlinelocal/" + name + ".before.py");
    try {
      PsiElement element = map.values().iterator().next().getParent();
      PyReferenceExpression ref = null;
      if (element instanceof PyReferenceExpression) {
        ref = (PyReferenceExpression)element;
        element = ((PyReferenceExpression)element).getReference().resolve();
      }
      PyInlineLocalHandler.invoke(myFixture.getProject(), myFixture.getEditor(), (PyTargetExpression)element, ref);
      if (expectedError != null) fail("expected error: '" + expectedError + "', got none");
    }
    catch (Exception e) {
      if (!Comparing.equal(e.getMessage(), expectedError)) {
        e.printStackTrace();
      }
      assertEquals(expectedError, e.getMessage());
      return;
    }
    myFixture.checkResultByFile("/refactoring/inlinelocal/" + name + ".after.py");
  }

  public void testSimple() throws Exception {
    doTest();
  }

  public void testPriority() throws Exception {
    doTest();
  }

  public void testNoDominator() throws Exception {
    doTest("Cannot perform refactoring.\nCannot find a single definition to inline.");
  }

  public void testDoubleDefinition() throws Exception {
    doTest("Cannot perform refactoring.\nAnother variable 'foo' definition is used together with inlined one.");
  }

  public void testMultiple() throws Exception {
    doTest();
  }

  public void testPy994() throws Exception {
    doTest();
  }
}
