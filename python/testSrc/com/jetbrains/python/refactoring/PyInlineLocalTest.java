package com.jetbrains.python.refactoring;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.refactoring.inline.PyInlineLocalHandler;

/**
 * @author Dennis.Ushakov
 */
public class PyInlineLocalTest extends PyTestCase {
  private void doTest() {
    doTest(null);
  }

  private void doTest(String expectedError) {
    final String name = getTestName(true);
    myFixture.configureByFile("/refactoring/inlinelocal/" + name + ".before.py");
    try {
      PsiElement element = TargetElementUtilBase.findTargetElement(myFixture.getEditor(),
                                                                   TargetElementUtilBase.getInstance().getReferenceSearchFlags());
      PyInlineLocalHandler handler = PyInlineLocalHandler.getInstance();
      handler.inlineElement(myFixture.getProject(), myFixture.getEditor(), element);
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

  public void testPy1585() {
    doTest();
  }

  public void testPy5832() {
    doTest();
  }
}
