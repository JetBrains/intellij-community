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
  private void doTest() {
    doTest(null);
  }

  private void doTest(String expectedError) {
    final String name = getTestName(true);
    final Map<String,PsiElement> map = configureByFile("/refactoring/inlinelocal/" + name + ".before.py");
    try {
      PsiElement element = map.values().iterator().next().getParent();
      PyReferenceExpression ref = null;
      while (element instanceof PyReferenceExpression) {
        ref = (PyReferenceExpression)element;
        PsiElement newElement = ((PyReferenceExpression)element).getReference().resolve();
        if (element == newElement) {
          break;
        }
        element = newElement;
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

  public void testSimple() {
    doTest();
  }

  public void testPriority() {
    doTest();
  }

  public void testNoDominator() {
    doTest("Cannot perform refactoring.\nCannot find a single definition to inline.");
  }

  public void testDoubleDefinition() {
    doTest("Cannot perform refactoring.\nAnother variable 'foo' definition is used together with inlined one.");
  }

  public void testMultiple() {
    doTest();
  }

  public void testPy994() {
    doTest();
  }
}
