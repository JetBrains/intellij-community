package com.jetbrains.python.refactoring;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.refactoring.invertBoolean.PyInvertBooleanProcessor;

/**
 * User : ktisha
 */
@TestDataPath("$CONTENT_ROOT/../testData/refactoring/invertBoolean/")
public class PyInvertBooleanTest extends PyTestCase {

  public void testSimple() { doTest(); }

  public void testNegate() { doTest(); }

  public void testParameter() { doTest(); }

  private void doTest() {
    myFixture.configureByFile("refactoring/invertBoolean/" + getTestName(true) + ".before.py");
    final PsiElement element = myFixture.getElementAtCaret();
    assertTrue(element instanceof PsiNamedElement);

    final PsiNamedElement target = (PsiNamedElement)element;
    final String name = target.getName();
    assertNotNull(name);
    new PyInvertBooleanProcessor(target, "not"+ StringUtil.toTitleCase(name)).run();
    myFixture.checkResultByFile("refactoring/invertBoolean/" + getTestName(true) + ".after.py");
  }

}
