package com.intellij.refactoring;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.invertBooleanMethod.InvertBooleanMethodProcessor;

/**
 * @author ven
 */
public class InvertBooleanMethodTest extends CodeInsightTestCase {
  private static final String TEST_ROOT = "/refactoring/invertBooleanMethod/";

  public void test1() throws Exception { doTest(); }

  public void test2() throws Exception { doTest(); } //inverting breaks overriding

  private void doTest() throws Exception {
    configureByFile(TEST_ROOT + getTestName(true) + ".java");
    PsiElement element = TargetElementUtil.findTargetElement(myEditor, TargetElementUtil.ELEMENT_NAME_ACCEPTED);
    assertTrue(element instanceof PsiMethod);

    final PsiMethod method = (PsiMethod)element;
    final String name = method.getName();
    new InvertBooleanMethodProcessor(method, name + "Inverted").run();
    checkResultByFile(TEST_ROOT + getTestName(true) + "_after.java");
  }

}
