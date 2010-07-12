package com.jetbrains.python.refactoring;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.psi.PsiElement;
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

  private void doTest(final String newName) {
    myFixture.configureByFile("refactoring/rename/" + getTestName(true) + ".py");
    myFixture.renameElementAtCaret(newName);
    myFixture.checkResultByFile("refactoring/rename/" + getTestName(true) + "_after.py");
  }
}
