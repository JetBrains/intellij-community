package com.jetbrains.python.refactoring;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;

/**
 * @author yole
 */
public class PyRenameTest extends PyLightFixtureTestCase {
  public void testRenameField() {  // PY-457
    doTest("qu");
  }

  public void testSearchInStrings() {  // PY-670
    myFixture.configureByFile("refactoring/rename/" + getTestName(true) + ".py");
    final PsiElement element = TargetElementUtilBase.findTargetElement(myFixture.getEditor(), TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED |
                                                                                        TargetElementUtilBase.ELEMENT_NAME_ACCEPTED);
    assertNotNull(element);
    myFixture.renameElement(element, "bar", true, false);
    myFixture.checkResultByFile("refactoring/rename/" + getTestName(true) + "_after.py");
  }

  public void testRenameParameter() {  // PY-385
    doTest("qu");
  }

  public void testRenameMultipleDefinitionsLocal() {  // PY-727
    doTest("qu");
  }

  public void testRenameInheritors() {
    doTest("qu");
  }

  public void testRenameInitCall() {  // PY-1364
    doTest("Qu");
  }

  public void testRenameInstanceVar() {  // PY-1472
    doTest("_x");
  }

  public void testRenameLocalWithComprehension() {  // PY-1618
    doTest("bar");
  }

  public void testRenameLocalWithComprehension2() {  // PY-1618
    doTest("bar");
  }

  public void testRenameLocalWithGenerator() {  // PY-3030
    doTest("bar");
  }

  public void testRenameLocalWithNestedGenerators() {  // PY-3030
    doTest("bar");
  }

  public void testUpdateAll() {  // PY-986
    doTest("bar");
  }

  public void testEpydocRenameParameter() {
    doTest("bar");
  }

  public void testEpydocRenameType() {
    doTest("Shazam");
  }

  public void testRenameGlobalWithoutToplevel() { // PY-3547
    doTest("bar");
  }

  public void testClassNameConflict() {  // PY-2390
    doRenameConflictTest("Foo", "A class named 'Foo' is already defined in classNameConflict.py");
  }

  public void testClassVsFunctionConflict() {
    doRenameConflictTest("Foo", "A function named 'Foo' is already defined in classVsFunctionConflict.py");
  }

  public void testClassVsVariableConflict() {
    doRenameConflictTest("Foo", "A variable named 'Foo' is already defined in classVsVariableConflict.py");
  }

  public void testNestedClassNameConflict() {
    doRenameConflictTest("Foo", "A class named 'Foo' is already defined in class 'C'");
  }

  public void testFunctionNameConflict() {
    doRenameConflictTest("foo", "A function named 'foo' is already defined in functionNameConflict.py");
  }

  public void testVariableNameConflict() {
    doRenameConflictTest("foo", "A variable named 'foo' is already defined in variableNameConflict.py");
  }

  private void doRenameConflictTest(String newName, String expectedConflict) {
    myFixture.configureByFile("refactoring/rename/" + getTestName(true) + ".py");
    try {
      myFixture.renameElementAtCaret(newName);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException ex) {
      assertEquals(expectedConflict, ex.getMessage());
      return;
    }
    fail("Expected conflict not reported");
  }

  private void doTest(final String newName) {
    myFixture.configureByFile("refactoring/rename/" + getTestName(true) + ".py");
    myFixture.renameElementAtCaret(newName);
    myFixture.checkResultByFile("refactoring/rename/" + getTestName(true) + "_after.py");
  }
}
